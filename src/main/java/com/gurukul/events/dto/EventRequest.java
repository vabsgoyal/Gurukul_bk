package com.gurukul.events.dto;

import com.gurukul.events.entity.EventStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Schema(description = "Event create/update payload")
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

}
