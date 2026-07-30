package com.gurukul.calls.service;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.calls.dto.CallEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Pushes a {@link CallEvent} to one owner's personal topic. Only ScheduledCallService and
 * CallSessionService use this (not part of WebSocketConfig's own bean graph), so - unlike
 * AnnouncementService - there is no circular dependency and SimpMessagingTemplate does not need
 * to be injected {@code @Lazy} here.
 */
@Component
public class CallEventPublisher {

	private final SimpMessagingTemplate messagingTemplate;

	public CallEventPublisher(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}

	public void sendTo(UUID schoolId, OwnerType ownerType, UUID ownerId, CallEvent event) {
		messagingTemplate.convertAndSend(topicFor(schoolId, ownerType, ownerId), event);
	}

	public static String topicFor(UUID schoolId, OwnerType ownerType, UUID ownerId) {
		return "/topic/users/" + schoolId + "/" + ownerType + "/" + ownerId + "/calls";
	}

}
