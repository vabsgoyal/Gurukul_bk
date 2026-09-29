package com.gurukul.calls.service;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.calls.dto.CallEvent;
import com.gurukul.calls.entity.CallLog;
import com.gurukul.notifications.service.OwnerNameResolver;
import com.gurukul.notifications.service.PushChannel;
import com.gurukul.notifications.service.PushNotificationService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Pushes a {@link CallEvent} to one owner's personal topic. Only ScheduledCallService and
 * CallSessionService use this (not part of WebSocketConfig's own bean graph), so - unlike
 * AnnouncementService - there is no circular dependency and SimpMessagingTemplate does not need
 * to be injected {@code @Lazy} here.
 *
 * <p>Also fires a push notification for the event types a backgrounded app needs to hear about:
 * an incoming call, a scheduled call starting, its 30-minute reminder, and a scheduled call being
 * cancelled. Every other event type here (accept/decline/end/...) only matters to a screen already
 * open. A missed call is pushed separately, via {@link #pushMissedCall} - the CALL_MISSED event
 * itself goes to the caller's open call screen, not to the person who missed it.
 */
@Component
public class CallEventPublisher {

	private final SimpMessagingTemplate messagingTemplate;
	private final PushNotificationService pushNotificationService;
	private final OwnerNameResolver ownerNameResolver;

	public CallEventPublisher(SimpMessagingTemplate messagingTemplate, PushNotificationService pushNotificationService,
			OwnerNameResolver ownerNameResolver) {
		this.messagingTemplate = messagingTemplate;
		this.pushNotificationService = pushNotificationService;
		this.ownerNameResolver = ownerNameResolver;
	}

	public void sendTo(UUID schoolId, OwnerType ownerType, UUID ownerId, CallEvent event) {
		messagingTemplate.convertAndSend(topicFor(schoolId, ownerType, ownerId), event);
		switch (event.getType()) {
			case INCOMING_CALL -> push(schoolId, ownerType, ownerId,
					ownerNameResolver.nameOf(schoolId, event.getCounterpartOwnerType(), event.getCounterpartOwnerId())
							.map(name -> name + " is calling").orElse("Incoming video call"),
					"Video call. Tap to answer.",
					Map.of("type", "INCOMING_CALL", "callLogId", String.valueOf(event.getCallLogId())));
			case SCHEDULED_CALL_STARTED -> push(schoolId, ownerType, ownerId,
					titleOr(event, "Your scheduled call"), "It has started. Tap to join.",
					Map.of("type", "SCHEDULED_CALL_STARTED", "scheduledCallId", String.valueOf(event.getScheduledCallId())));
			case SCHEDULED_CALL_REMINDER -> push(schoolId, ownerType, ownerId,
					titleOr(event, "Your scheduled call"), startsIn(event.getScheduledAt()),
					Map.of("type", "SCHEDULED_CALL_REMINDER", "scheduledCallId", String.valueOf(event.getScheduledCallId())));
			// A ringing call the caller hangs up is also CALL_CANCELLED, but without a scheduledCallId -
			// that one reaches the callee as a missed call instead (see pushMissedCall).
			case CALL_CANCELLED -> {
				if (event.getScheduledCallId() != null) {
					push(schoolId, ownerType, ownerId, "Call cancelled",
							titleOr(event, "A scheduled call") + " has been cancelled.",
							Map.of("type", "SCHEDULED_CALL_CANCELLED", "scheduledCallId", String.valueOf(event.getScheduledCallId())));
				}
			}
			default -> {
			}
		}
	}

	/** For the callee of a 1:1 call that rang out, or that the caller hung up before it was answered. */
	public void pushMissedCall(CallLog callLog) {
		if (callLog.getCalleeOwnerType() == null) {
			return;
		}
		String body = ownerNameResolver.nameOf(callLog.getSchoolId(), callLog.getCallerOwnerType(), callLog.getCallerOwnerId())
				.map(name -> "From " + name + ".").orElse("Tap to see your call history.");
		push(callLog.getSchoolId(), callLog.getCalleeOwnerType(), callLog.getCalleeOwnerId(), "Missed video call", body,
				Map.of("type", "CALL_MISSED", "callLogId", String.valueOf(callLog.getId())));
	}

	private void push(UUID schoolId, OwnerType ownerType, UUID ownerId, String title, String body, Map<String, Object> data) {
		pushNotificationService.sendToOwner(schoolId, ownerType, ownerId, PushChannel.CALLS, title, body, data);
	}

	private static String titleOr(CallEvent event, String fallback) {
		return event.getTitle() != null && !event.getTitle().isBlank() ? event.getTitle() : fallback;
	}

	/**
	 * The reminder goes out 30 minutes ahead, but a call booked less than 30 minutes out gets it
	 * on the next sweep - so say how long is actually left rather than always "30 minutes".
	 */
	static String startsIn(Instant scheduledAt) {
		if (scheduledAt == null) {
			return "Starts soon.";
		}
		long minutes = (Duration.between(Instant.now(), scheduledAt).toSeconds() + 59) / 60;
		if (minutes <= 1) {
			return "Starts in a minute.";
		}
		return "Starts in " + minutes + " minutes.";
	}

	public static String topicFor(UUID schoolId, OwnerType ownerType, UUID ownerId) {
		return "/topic/users/" + schoolId + "/" + ownerType + "/" + ownerId + "/calls";
	}

}
