package com.gurukul.events.dto;

import com.gurukul.events.entity.EventCategory;
import com.gurukul.events.entity.EventParticipationStatus;
import com.gurukul.events.entity.EventParticipationType;
import com.gurukul.events.entity.EventRsvpStatus;
import com.gurukul.events.entity.EventScope;
import com.gurukul.events.entity.EventStatus;
import com.gurukul.events.entity.SchoolEvent;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "School event record")
public class EventResponse {

	private UUID id;
	private UUID schoolId;
	private String name;
	private String description;
	private LocalDate eventDate;
	private EventStatus status;
	private boolean inflowEnabled;
	private boolean outflowEnabled;
	private EventCategory category;
	private EventScope scope;
	private UUID sectionId;
	private String className;
	private String venue;
	private Instant startAt;
	private Instant endAt;
	@Schema(description = "Only meaningful when participationType is set - derived from cancelled/startAt/endAt")
	private EventParticipationStatus participationStatus;
	private EventParticipationType participationType;
	private List<EventRequest.RegistrationFieldDefinition> registrationFields;
	private UUID createdByEmployeeId;
	private String createdByEmployeeName;
	@Schema(description = "The caller's own RSVP, if participationType=RSVP and they've responded - "
			+ "the only way a non-creator/admin can ever read their own RSVP back")
	private EventRsvpStatus myRsvpStatus;
	@Schema(description = "The caller's own registration answers, if participationType=REGISTRATION "
			+ "and they've submitted - the only way a non-creator/admin can ever read their own registration back")
	private Map<String, String> myRegistrationAnswers;
	private Instant createdAt;
	private Instant updatedAt;

	public static EventResponse from(SchoolEvent event, EventParticipationStatus participationStatus,
			List<EventRequest.RegistrationFieldDefinition> registrationFields,
			EventRsvpStatus myRsvpStatus, Map<String, String> myRegistrationAnswers) {
		return new EventResponse(
				event.getId(),
				event.getSchoolId(),
				event.getName(),
				event.getDescription(),
				event.getEventDate(),
				event.getStatus(),
				event.isInflowEnabled(),
				event.isOutflowEnabled(),
				event.getCategory(),
				event.getScope(),
				event.getSectionId(),
				event.getClassName(),
				event.getVenue(),
				event.getStartAt(),
				event.getEndAt(),
				participationStatus,
				event.getParticipationType(),
				registrationFields,
				event.getCreatedByEmployee() != null ? event.getCreatedByEmployee().getId() : null,
				event.getCreatedByEmployee() != null ? event.getCreatedByEmployee().getName() : null,
				myRsvpStatus,
				myRegistrationAnswers,
				event.getCreatedAt(),
				event.getUpdatedAt()
		);
	}

}
