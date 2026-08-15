package com.gurukul.calls.dto;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.calls.entity.CallLog;
import com.gurukul.calls.entity.CallProvider;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Push payload sent over a user's personal WebSocket topic (see CallEventPublisher /
 * StompSubscribeAuthorizationInterceptor). Never exposed through a REST controller, so it isn't
 * scanned into the OpenAPI schema - no @Schema needed, matching ChatDtos.MessageResponse.
 */
@Getter
@AllArgsConstructor
public class CallEvent {

	public enum Type {
		INCOMING_CALL, CALL_ACCEPTED, CALL_DECLINED, CALL_BUSY, CALL_MISSED, CALL_CANCELLED, CALL_ENDED,
		SCHEDULED_CALL_STARTED, SCHEDULED_CALL_REMINDER
	}

	private Type type;
	private UUID callLogId;
	private UUID scheduledCallId;
	private String roomName;
	/** JITSI: roomName is a bare slug, join via meet.jit.si/roomName. GOOGLE_MEET: roomName IS the
	 *  full join URL directly. Null on events that carry no roomName (e.g. reminders). */
	private CallProvider provider;
	private OwnerType counterpartOwnerType;
	private UUID counterpartOwnerId;
	private String title;
	private Instant scheduledAt;

	public static CallEvent incomingCall(CallLog log, OwnerType callerType, UUID callerId) {
		return new CallEvent(Type.INCOMING_CALL, log.getId(), null, log.getRoomName(), log.getProvider(), callerType, callerId, null, null);
	}

	public static CallEvent simple(Type type, CallLog log) {
		return new CallEvent(type, log.getId(), null, log.getRoomName(), log.getProvider(), null, null, null, null);
	}

	public static CallEvent scheduledStarted(UUID scheduledCallId, String roomName, CallProvider provider, String title) {
		return new CallEvent(Type.SCHEDULED_CALL_STARTED, null, scheduledCallId, roomName, provider, null, null, title, null);
	}

	public static CallEvent scheduledReminder(UUID scheduledCallId, String title, Instant scheduledAt) {
		return new CallEvent(Type.SCHEDULED_CALL_REMINDER, null, scheduledCallId, null, null, null, null, title, scheduledAt);
	}

}
