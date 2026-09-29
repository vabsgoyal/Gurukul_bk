package com.gurukul.notifications.dto;

import com.gurukul.notifications.entity.Notification;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NotificationDtos {

	@Getter @Setter
	public static class RegisterDeviceTokenRequest {
		@NotBlank private String expoPushToken;
	}

	@Getter @AllArgsConstructor
	@Schema(description = "One inbox entry - a copy of a push the caller was sent")
	public static class NotificationResponse {
		private UUID id;
		@Schema(description = "The push's data.type, e.g. ABSENCE_ALERT, FEE_DUE, ANNOUNCEMENT, NEW_MESSAGE")
		private String type;
		private String title;
		private String body;
		@Schema(description = "The push's data payload - the same keys the app routes a push tap on")
		private Map<String, Object> data;
		private Instant readAt;
		private Instant createdAt;

		public static NotificationResponse from(Notification notification, Map<String, Object> data) {
			return new NotificationResponse(notification.getId(), notification.getType(), notification.getTitle(),
					notification.getBody(), data, notification.getReadAt(), notification.getCreatedAt());
		}
	}

	@Getter @AllArgsConstructor
	public static class NotificationPageResponse {
		private List<NotificationResponse> notifications;
		private boolean hasMore;
	}

	@Getter @AllArgsConstructor
	public static class UnreadCountResponse {
		private long unread;
	}

}
