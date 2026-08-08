package com.gurukul.notifications.service;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.notifications.entity.DeviceToken;
import com.gurukul.notifications.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
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
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationService {

	private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";

	private final DeviceTokenRepository deviceTokenRepository;
	private final RestClient restClient = RestClient.create();

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
		try {
			List<Map<String, Object>> messages = tokens.stream()
					.map(token -> Map.<String, Object>of(
							"to", token,
							"title", title,
							"body", body,
							"data", data,
							"sound", "default"))
					.toList();
			restClient.post()
					.uri(EXPO_PUSH_URL)
					.contentType(MediaType.APPLICATION_JSON)
					.body(messages)
					.retrieve()
					.toBodilessEntity();
		} catch (Exception e) {
			log.warn("Push notification send failed for {} token(s) - proceeding without it", tokens.size(), e);
		}
	}

}
