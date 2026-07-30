package com.gurukul.calls.dto;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.calls.entity.CallInvitee;
import com.gurukul.calls.entity.CallLog;
import com.gurukul.calls.entity.CallOutcome;
import com.gurukul.calls.entity.CallStatus;
import com.gurukul.calls.entity.RsvpStatus;
import com.gurukul.calls.entity.ScheduledCall;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class CallDtos {

	@Getter @Setter
	@Schema(name = "ScheduleCallRequest")
	public static class ScheduleCallRequest {
		@NotBlank private String title;
		@NotNull private OwnerType inviteeOwnerType;
		@NotEmpty private List<UUID> inviteeOwnerIds;
		@NotNull @Future private Instant scheduledAt;
	}

	@Getter @Setter
	@Schema(name = "RsvpRequest")
	public static class RsvpRequest {
		@NotNull private RsvpStatus status;
	}

	@Getter @Setter
	@Schema(name = "StartImmediateCallRequest")
	public static class StartImmediateCallRequest {
		@NotNull private OwnerType calleeOwnerType;
		@NotNull private UUID calleeOwnerId;
	}

	@Getter @AllArgsConstructor
	@Schema(name = "CallInviteeResponse")
	public static class InviteeResponse {
		private OwnerType ownerType;
		private UUID ownerId;
		private RsvpStatus rsvpStatus;

		public static InviteeResponse from(CallInvitee invitee) {
			return new InviteeResponse(invitee.getOwnerType(), invitee.getOwnerId(), invitee.getRsvpStatus());
		}
	}

	@Getter @AllArgsConstructor
	@Schema(name = "ScheduledCallResponse")
	public static class ScheduledCallResponse {
		private UUID id;
		private String title;
		private OwnerType hostOwnerType;
		private UUID hostOwnerId;
		private Instant scheduledAt;
		private String roomName;
		private CallStatus status;
		private List<InviteeResponse> invitees;

		public static ScheduledCallResponse from(ScheduledCall call, List<CallInvitee> invitees) {
			return new ScheduledCallResponse(
					call.getId(),
					call.getTitle(),
					call.getHostOwnerType(),
					call.getHostOwnerId(),
					call.getScheduledAt(),
					call.getRoomName(),
					call.getStatus(),
					invitees.stream().map(InviteeResponse::from).toList());
		}
	}

	@Getter @AllArgsConstructor
	@Schema(name = "MyInviteResponse")
	public static class MyInviteResponse {
		private UUID scheduledCallId;
		private String title;
		private OwnerType hostOwnerType;
		private UUID hostOwnerId;
		private Instant scheduledAt;
		private CallStatus status;
		private RsvpStatus myRsvpStatus;

		public static MyInviteResponse from(CallInvitee invitee) {
			ScheduledCall call = invitee.getScheduledCall();
			return new MyInviteResponse(
					call.getId(), call.getTitle(), call.getHostOwnerType(), call.getHostOwnerId(),
					call.getScheduledAt(), call.getStatus(), invitee.getRsvpStatus());
		}
	}

	@Getter @AllArgsConstructor
	@Schema(name = "CallSessionResponse")
	public static class CallSessionResponse {
		private UUID callLogId;
		private String roomName;
		private CallOutcome outcome;

		public static CallSessionResponse from(CallLog callLog) {
			return new CallSessionResponse(callLog.getId(), callLog.getRoomName(), callLog.getOutcome());
		}
	}

	@Getter @AllArgsConstructor
	@Schema(name = "CallLogResponse")
	public static class CallLogResponse {
		private UUID id;
		private UUID scheduledCallId;
		private OwnerType callerOwnerType;
		private UUID callerOwnerId;
		private OwnerType calleeOwnerType;
		private UUID calleeOwnerId;
		private Instant startedAt;
		private Instant endedAt;
		private Long durationSeconds;
		private CallOutcome outcome;

		public static CallLogResponse from(CallLog log) {
			return new CallLogResponse(
					log.getId(),
					log.getScheduledCallId(),
					log.getCallerOwnerType(),
					log.getCallerOwnerId(),
					log.getCalleeOwnerType(),
					log.getCalleeOwnerId(),
					log.getStartedAt(),
					log.getEndedAt(),
					log.getDurationSeconds(),
					log.getOutcome());
		}
	}

}
