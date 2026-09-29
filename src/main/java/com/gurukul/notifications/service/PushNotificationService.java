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
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Sends push notifications via Expo's push service (https://exp.host) rather than talking to
 * Firebase/APNs directly - this app is an Expo managed-workflow build, so Expo's own (free) push
 * service is the natural fit: it brokers delivery to FCM/APNs using each device's Expo push token.
 * The Firebase project (google-services.json in the app) and its FCM V1 key live on the app/EAS
 * side; this backend holds no Firebase credentials.
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
	private final NotificationInboxService notificationInboxService;

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

	public void sendToOwner(UUID schoolId, OwnerType ownerType, UUID ownerId, PushChannel channel,
			String title, String body, Map<String, Object> data) {
		sendToRecipients(schoolId, List.of(new Recipient(ownerType, ownerId)), channel, title, body, data);
	}

	public void sendToRecipients(UUID schoolId, List<Recipient> recipients, PushChannel channel,
			String title, String body, Map<String, Object> data) {
		sendEach(schoolId, channel, List.of(new Notification(recipients, title, body, data)));
	}

	/** One notification's content and who it goes to - see {@link #sendEach}. */
	public record Notification(List<Recipient> recipients, String title, String body, Map<String, Object> data) {
	}

	/**
	 * Several differently-worded notifications (one per child's parents, say) in as few Expo
	 * requests as possible - Expo takes a mix of messages in one batch, so this costs one HTTP call
	 * per 100 devices rather than one per notification.
	 *
	 * <p>Every owner type (employee, student, parent) is looked up. Within one notification tokens
	 * are de-duplicated, so someone listed twice - a parent of two children in the same
	 * announcement's scope - gets it once, not twice.
	 *
	 * <p>Every notification is also saved to each recipient's inbox (see NotificationInboxService),
	 * device or no device, so it can be re-read in the app later.
	 */
	public void sendEach(UUID schoolId, PushChannel channel, List<Notification> notifications) {
		for (Notification notification : notifications) {
			saveToInbox(schoolId, notification);
		}
		push(schoolId, channel, notifications);
	}

	/**
	 * For alerts that must reach each recipient at most once per {@code dedupeKey} (an absence per
	 * child per day, a fee reminder per assessment per window): only recipients with no inbox row for
	 * that key yet get a row and a push. Returns how many recipients were newly notified. Fails open
	 * like everything else here - a failed claim (including losing a race to a concurrent caller,
	 * which the unique index turns into an exception) sends nothing and returns 0.
	 */
	public int sendOnce(UUID schoolId, PushChannel channel, Notification notification, String dedupeKey) {
		List<Recipient> fresh;
		try {
			fresh = notificationInboxService.claim(schoolId, notification.recipients(), notification.title(),
					notification.body(), notification.data(), dedupeKey);
		} catch (Exception e) {
			log.info("Alert {} not sent - already claimed or inbox write failed: {}", dedupeKey, e.getMessage());
			return 0;
		}
		if (!fresh.isEmpty()) {
			push(schoolId, channel, List.of(new Notification(fresh, notification.title(), notification.body(),
					notification.data())));
		}
		return fresh.size();
	}

	private void saveToInbox(UUID schoolId, Notification notification) {
		if (notification.recipients().isEmpty()) {
			return;
		}
		try {
			notificationInboxService.record(schoolId, notification.recipients(), notification.title(),
					notification.body(), notification.data());
		} catch (Exception e) {
			log.warn("Saving {} notification(s) to the inbox failed - pushing anyway",
					notification.recipients().size(), e);
		}
	}

	private void push(UUID schoolId, PushChannel channel, List<Notification> notifications) {
		List<Map<String, Object>> messages = new ArrayList<>();
		for (Notification notification : notifications) {
			for (String token : tokensFor(schoolId, notification.recipients())) {
				messages.add(message(token, channel, notification));
			}
		}
		for (int from = 0; from < messages.size(); from += EXPO_MAX_BATCH) {
			sendBatch(messages.subList(from, Math.min(from + EXPO_MAX_BATCH, messages.size())));
		}
	}

	private Set<String> tokensFor(UUID schoolId, List<Recipient> recipients) {
		Set<String> tokens = new LinkedHashSet<>();
		if (recipients.isEmpty()) {
			return tokens;
		}
		Map<OwnerType, List<UUID>> idsByType = recipients.stream().collect(Collectors.groupingBy(
				Recipient::ownerType, () -> new EnumMap<>(OwnerType.class),
				Collectors.mapping(Recipient::ownerId, Collectors.collectingAndThen(Collectors.toSet(), List::copyOf))));
		idsByType.forEach((ownerType, ownerIds) -> deviceTokenRepository
				.findAllBySchoolIdAndOwnerTypeAndOwnerIdIn(schoolId, ownerType, ownerIds)
				.forEach(token -> tokens.add(token.getExpoPushToken())));
		return tokens;
	}

	/**
	 * priority "high" lets Android show the notification straight away even when the phone is
	 * dozing; Expo's default can hold it back until the device next wakes. channelId picks the
	 * Android channel - and with it the channel's sound, importance and the user's mute setting.
	 */
	private static Map<String, Object> message(String token, PushChannel channel, Notification notification) {
		return Map.of(
				"to", token,
				"title", notification.title(),
				"body", notification.body(),
				"data", notification.data(),
				"sound", "default",
				"channelId", channel.androidChannelId(),
				"priority", "high");
	}

	private void sendBatch(List<Map<String, Object>> messages) {
		List<String> tokens = messages.stream().map(m -> (String) m.get("to")).toList();
		try {
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
