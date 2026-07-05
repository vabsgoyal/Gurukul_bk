package com.gurukul.events.dto;

import com.gurukul.events.entity.EventStatus;
import com.gurukul.events.entity.SchoolEvent;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
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
	private Instant createdAt;
	private Instant updatedAt;

	public static EventResponse from(SchoolEvent event) {
		return new EventResponse(
				event.getId(),
				event.getSchoolId(),
				event.getName(),
				event.getDescription(),
				event.getEventDate(),
				event.getStatus(),
				event.isInflowEnabled(),
				event.isOutflowEnabled(),
				event.getCreatedAt(),
				event.getUpdatedAt()
		);
	}

}
