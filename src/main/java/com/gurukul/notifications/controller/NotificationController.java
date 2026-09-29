package com.gurukul.notifications.controller;

import com.gurukul.auth.security.AuthContext;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.ApiResponse;
import com.gurukul.notifications.dto.NotificationDtos.NotificationPageResponse;
import com.gurukul.notifications.dto.NotificationDtos.NotificationResponse;
import com.gurukul.notifications.dto.NotificationDtos.UnreadCountResponse;
import com.gurukul.notifications.entity.Notification;
import com.gurukul.notifications.service.NotificationInboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "The caller's own notification inbox - a copy of every push they were "
		+ "sent. Requires X-School-Id and Authorization headers.")
public class NotificationController {

	static final int MAX_PAGE_SIZE = 100;

	private final NotificationInboxService notificationInboxService;

	@GetMapping("/api/v1/notifications")
	@Operation(summary = "My notifications, newest first")
	public ApiResponse<NotificationPageResponse> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "30") int size) {
		AuthPrincipal principal = AuthContext.current();
		Slice<Notification> result = notificationInboxService.listMine(principal,
				PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));
		return ApiResponse.success(new NotificationPageResponse(
				result.getContent().stream()
						.map(n -> NotificationResponse.from(n, notificationInboxService.parseData(n)))
						.toList(),
				result.hasNext()));
	}

	@GetMapping("/api/v1/notifications/unread-count")
	@Operation(summary = "How many of my notifications are unread")
	public ApiResponse<UnreadCountResponse> unreadCount() {
		return ApiResponse.success(new UnreadCountResponse(notificationInboxService.unreadCount(AuthContext.current())));
	}

	@PostMapping("/api/v1/notifications/{id}/read")
	@Operation(summary = "Mark one of my notifications read", description = "404 if it isn't the caller's own.")
	public ApiResponse<NotificationResponse> markRead(@PathVariable UUID id) {
		Notification notification = notificationInboxService.markRead(AuthContext.current(), id);
		return ApiResponse.success(NotificationResponse.from(notification, notificationInboxService.parseData(notification)));
	}

	@PostMapping("/api/v1/notifications/read-all")
	@Operation(summary = "Mark all my notifications read")
	public ApiResponse<UnreadCountResponse> markAllRead() {
		notificationInboxService.markAllRead(AuthContext.current());
		return ApiResponse.success(new UnreadCountResponse(0));
	}

}
