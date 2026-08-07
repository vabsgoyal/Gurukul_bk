package com.gurukul.events.dto;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.events.entity.EventRsvpStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RSVP/registration/poll request and response shapes for events with a participation model set. */
public class EventDtos {

	@Getter @Setter
	@Schema(name = "RsvpRequest")
	public static class RsvpRequest {
		@NotNull private EventRsvpStatus status;
	}

	@Getter @Setter
	@Schema(name = "SubmitRegistrationRequest")
	public static class SubmitRegistrationRequest {
		@NotEmpty private Map<String, String> answers;
	}

	@Getter @Setter
	@Schema(name = "CreatePollOptionsRequest")
	public static class CreatePollOptionsRequest {
		@NotEmpty private List<@NotBlank String> options;
	}

	@Getter @Setter
	@Schema(name = "VoteRequest")
	public static class VoteRequest {
		@NotNull private UUID optionId;
	}

	@Getter @AllArgsConstructor
	@Schema(name = "RsvpRosterEntry")
	public static class RsvpRosterEntry {
		private UUID ownerId;
		private OwnerType ownerType;
		private String name;
		private EventRsvpStatus status;
	}

	@Getter @AllArgsConstructor
	@Schema(name = "RegistrationEntry")
	public static class RegistrationEntry {
		private UUID id;
		private UUID ownerId;
		private OwnerType ownerType;
		private String name;
		private Map<String, String> answers;
		private Instant submittedAt;
	}

	@Getter @AllArgsConstructor
	@Schema(name = "PollOptionResult")
	public static class PollOptionResult {
		private UUID id;
		private String label;
		private long voteCount;
	}

	@Getter @AllArgsConstructor
	@Schema(name = "PollResponse")
	public static class PollResponse {
		private List<PollOptionResult> options;
		@Schema(description = "The caller's own vote, if any")
		private UUID myVoteOptionId;
	}

}
