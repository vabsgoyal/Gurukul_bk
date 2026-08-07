package com.gurukul.events.dto;

import com.gurukul.events.entity.EventCategory;
import com.gurukul.events.entity.EventParticipationType;
import com.gurukul.events.entity.EventScope;
import com.gurukul.events.entity.EventStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Schema(description = "Event create/update payload. category/scope/startAt/endAt/participationType "
		+ "are only required when opting into the participation flow (RSVP/registration/poll) - a "
		+ "plain finance-tracking event can omit all of them, exactly as before.")
public class EventRequest {

	@NotBlank
	@Schema(description = "Event name", example = "Annual Day")
	private String name;

	@Schema(description = "Event description")
	private String description;

	@NotNull
	@Schema(description = "Event date", example = "2026-12-15")
	private LocalDate eventDate;

	@Schema(description = "Event status", example = "DRAFT")
	private EventStatus status;

	@Schema(description = "Whether inflow collections are enabled")
	private Boolean inflowEnabled;

	@Schema(description = "Whether outflow expenses are enabled")
	private Boolean outflowEnabled;

	@Schema(description = "Required to enable the participation flow (RSVP/registration/poll)")
	private EventCategory category;

	@Schema(description = "Required to enable the participation flow")
	private EventScope scope;

	@Schema(description = "Required when scope=CLASS")
	private UUID sectionId;

	@Schema(description = "Required when scope=GRADE, e.g. \"Grade 8\"")
	private String className;

	private String venue;

	@Schema(description = "Required to enable the participation flow")
	private Instant startAt;

	@Schema(description = "Required to enable the participation flow")
	private Instant endAt;

	@Schema(description = "RSVP/REGISTRATION/POLL/NONE - omit entirely for a plain finance-tracking event")
	private EventParticipationType participationType;

	@Schema(description = "Required when participationType=REGISTRATION")
	@Valid
	private List<RegistrationFieldDefinition> registrationFields;

	@Getter @Setter
	@Schema(name = "RegistrationFieldDefinition")
	public static class RegistrationFieldDefinition {
		@NotBlank private String key;
		@NotBlank private String label;
		private boolean required;
	}

}
