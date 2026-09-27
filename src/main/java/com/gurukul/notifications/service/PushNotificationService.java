package com.gurukul.notifications.service;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.notifications.entity.DeviceToken;
import com.gurukul.notifications.repository.DeviceTokenRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends push notifications via Expo's push service (https://exp.host) rather than talking to
 * Firebase/APNs directly - this app is an Expo managed-workflow build, so Expo's own (free) push
 * service is the natural fit: no separate Firebase project, credentials, or native config needed,
 * it brokers delivery to FCM/APNs behind the scenes using each device's Expo push token.
 *
 * <p>Fails open, same philosophy as JitsiBotService: any error talking to Expo (or simply having
 * no registered device for the recipient) is caught/absorbed and never propagated - a push is a
 * best-effort convenience for a backgrounded app, not something that should ever block or fail
 * the action that triggered it (sending a message, starting a call, posting an announcement).
 *
 * <p>Expo answers 200 even when individual messages fail - each message gets its own ticket with
 * status "error" (e.g. DeviceNotRegistered for an uninstalled app, InvalidCredentials when the
 * Android FCM key isn't uploaded to EAS). Those are logged, and DeviceNotRegistered tokens are
 * deleted so dead devices stop being targeted. Only send-time tickets are checked; delivery
 * receipts (Expo's getReceipts, fetched later) are not.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationService {

	static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";
	/** Expo rejects requests with more messages than this. */
	static final int EXPO_MAX_BATCH = 100;

	private final DeviceTokenRepository deviceTokenRepository;
	private final RestClient expoPushRestClient;

	public record Recipient(OwnerType ownerType, UUID ownerId) {
	}

	@Transactional
	public void registerToken(AuthPrincipal principal, String expoPushToken) {
		DeviceToken token = deviceTokenRepository.findByExpoPushToken(expoPushToken).orElseGet(DeviceToken::new);
		token.setSchoolId(principal.getSchoolId());
		token.setOwnerType(principal.getOwnerType());
		token.setOwnerId(principal.getOwnerId());
		token.setExpoPushToken(expoPushToken);
		deviceTokenRepository.save(token);
	}

	public void sendToOwner(UUID schoolId, OwnerType ownerType, UUID ownerId, String title, String body, Map<String, Object> data) {
		sendToRecipients(schoolId, List.of(new Recipient(ownerType, ownerId)), title, body, data);
	}

	public void sendToRecipients(UUID schoolId, List<Recipient> recipients, String title, String body, Map<String, Object> data) {
		if (recipients.isEmpty()) {
			return;
		}
		List<UUID> employeeIds = recipients.stream().filter(r -> r.ownerType() == OwnerType.EMPLOYEE).map(Recipient::ownerId).toList();
		List<UUID> studentIds = recipients.stream().filter(r -> r.ownerType() == OwnerType.STUDENT).map(Recipient::ownerId).toList();

		List<String> tokens = new ArrayList<>();
		if (!employeeIds.isEmpty()) {
			tokens.addAll(deviceTokenRepository.findAllBySchoolIdAndOwnerTypeAndOwnerIdIn(schoolId, OwnerType.EMPLOYEE, employeeIds)
					.stream().map(DeviceToken::getExpoPushToken).toList());
		}
		if (!studentIds.isEmpty()) {
			tokens.addAll(deviceTokenRepository.findAllBySchoolIdAndOwnerTypeAndOwnerIdIn(schoolId, OwnerType.STUDENT, studentIds)
					.stream().map(DeviceToken::getExpoPushToken).toList());
		}
		send(tokens, title, body, data);
	}

	private void send(List<String> tokens, String title, String body, Map<String, Object> data) {
		if (tokens.isEmpty()) {
			return;
		}
		for (int from = 0; from < tokens.size(); from += EXPO_MAX_BATCH) {
			sendBatch(tokens.subList(from, Math.min(from + EXPO_MAX_BATCH, tokens.size())), title, body, data);
		}
	}

	private void sendBatch(List<String> tokens, String title, String body, Map<String, Object> data) {
		try {
			List<Map<String, Object>> messages = tokens.stream()
					.map(token -> Map.<String, Object>of(
							"to", token,
							"title", title,
							"body", body,
							"data", data,
							"sound", "default"))
					.toList();
			ExpoPushResponse response = expoPushRestClient.post()
					.uri(EXPO_PUSH_URL)
					.contentType(MediaType.APPLICATION_JSON)
					.body(messages)
					.retrieve()
					.body(ExpoPushResponse.class);
			handleTickets(tokens, response);
		} catch (Exception e) {
			log.warn("Push notification send failed for {} token(s) - proceeding without it", tokens.size(), e);
		}
	}

	/** Tickets come back in the same order as the messages sent. */
	private void handleTickets(List<String> tokens, ExpoPushResponse response) {
		if (response == null) {
			return;
		}
		if (response.errors() != null && !response.errors().isEmpty()) {
			log.warn("Expo rejected a push request for {} token(s): {}", tokens.size(), response.errors());
		}
		if (response.data() == null) {
			return;
		}
		List<String> unregistered = new ArrayList<>();
		for (int i = 0; i < Math.min(tokens.size(), response.data().size()); i++) {
			ExpoTicket ticket = response.data().get(i);
			if (!"error".equals(ticket.status())) {
				continue;
			}
			String error = ticket.details() != null ? ticket.details().error() : null;
			log.warn("Expo push failed for token {}: {} ({})", redact(tokens.get(i)), error, ticket.message());
			if ("DeviceNotRegistered".equals(error)) {
				unregistered.add(tokens.get(i));
			}
		}
		removeTokens(unregistered);
	}

	private void removeTokens(Collection<String> expoPushTokens) {
		for (String token : expoPushTokens) {
			deviceTokenRepository.findByExpoPushToken(token).ifPresent(deviceTokenRepository::delete);
		}
		if (!expoPushTokens.isEmpty()) {
			log.info("Removed {} push token(s) Expo reported as no longer registered", expoPushTokens.size());
		}
	}

	/** Push tokens are credentials for messaging that device - never log them whole. */
	private static String redact(String token) {
		return token.length() <= 24 ? token : token.substring(0, 24) + "...]";
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ExpoPushResponse(List<ExpoTicket> data, List<Map<String, Object>> errors) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ExpoTicket(String status, String id, String message, ExpoTicketDetails details) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ExpoTicketDetails(String error) {
	}

}
