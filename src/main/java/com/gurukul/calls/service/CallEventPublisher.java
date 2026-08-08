package com.gurukul.calls.service;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.calls.dto.CallEvent;
import com.gurukul.notifications.service.PushNotificationService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Pushes a {@link CallEvent} to one owner's personal topic. Only ScheduledCallService and
 * CallSessionService use this (not part of WebSocketConfig's own bean graph), so - unlike
 * AnnouncementService - there is no circular dependency and SimpMessagingTemplate does not need
 * to be injected {@code @Lazy} here.
 *
 * <p>Also fires a push notification for the two event types that need to reach a backgrounded
 * app (an incoming call would otherwise ring only while the app holds a live STOMP connection) -
 * every other event type here (accept/decline/end/...) only matters to a screen already open.
 */
@Component
public class CallEventPublisher {

	private final SimpMessagingTemplate messagingTemplate;
	private final PushNotificationService pushNotificationService;

	public CallEventPublisher(SimpMessagingTemplate messagingTemplate, PushNotificationService pushNotificationService) {
		this.messagingTemplate = messagingTemplate;
		this.pushNotificationService = pushNotificationService;
	}

	public void sendTo(UUID schoolId, OwnerType ownerType, UUID ownerId, CallEvent event) {
		messagingTemplate.convertAndSend(topicFor(schoolId, ownerType, ownerId), event);
		if (event.getType() == CallEvent.Type.INCOMING_CALL) {
			pushNotificationService.sendToOwner(schoolId, ownerType, ownerId,
					"Incoming video call", "Tap to answer", Map.of("type", "INCOMING_CALL", "callLogId", String.valueOf(event.getCallLogId())));
		} else if (event.getType() == CallEvent.Type.SCHEDULED_CALL_STARTED) {
			pushNotificationService.sendToOwner(schoolId, ownerType, ownerId,
					event.getTitle() != null ? event.getTitle() : "Scheduled call starting", "Tap to join",
					Map.of("type", "SCHEDULED_CALL_STARTED", "scheduledCallId", String.valueOf(event.getScheduledCallId())));
		}
	}

	public static String topicFor(UUID schoolId, OwnerType ownerType, UUID ownerId) {
		return "/topic/users/" + schoolId + "/" + ownerType + "/" + ownerId + "/calls";
	}

}
