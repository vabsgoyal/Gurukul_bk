package com.gurukul.timetable.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TimetableDtos {

	@Getter @Setter
	@Schema(description = "One period of the bell schedule")
	public static class PeriodRequest {
		@NotNull @Min(1) @Max(15) private Integer periodNumber;
		@NotNull @JsonFormat(pattern = "HH:mm") @Schema(example = "08:30", type = "string") private LocalTime startTime;
		@NotNull @JsonFormat(pattern = "HH:mm") @Schema(example = "09:10", type = "string") private LocalTime endTime;
		private boolean breakPeriod;
		@Size(max = 50) private String label;
	}

	@Getter @Setter
	@Schema(description = "The school's whole bell schedule plus the Saturday switch")
	public static class PeriodScheduleRequest {
		private boolean saturdayEnabled;
		@NotNull @Size(min = 1, max = 15) @Valid private List<PeriodRequest> periods = new ArrayList<>();
	}

	@Getter @AllArgsConstructor
	public static class PeriodResponse {
		private int periodNumber;
		@JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "08:30") private LocalTime startTime;
		@JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "09:10") private LocalTime endTime;
		private boolean breakPeriod;
		private String label;
	}

	@Getter @AllArgsConstructor
	public static class PeriodScheduleResponse {
		private boolean saturdayEnabled;
		@Schema(description = "School days in order: MONDAY..FRIDAY, plus SATURDAY when enabled")
		private List<DayOfWeek> days;
		private List<PeriodResponse> periods;
	}

	@Getter @Setter
	public static class SlotRequest {
		@NotNull private DayOfWeek dayOfWeek;
		@NotNull private Integer periodNumber;
		@NotNull private UUID subjectId;
		@NotNull private UUID teacherId;
	}

	@Getter @Setter
	@Schema(description = "The section's complete weekly timetable; replaces whatever was saved before. "
			+ "Omit a day+period to leave it free.")
	public static class SectionTimetableRequest {
		@NotNull @Size(max = 200) @Valid private List<SlotRequest> slots = new ArrayList<>();
	}

	@Getter @AllArgsConstructor
	public static class SlotResponse {
		private DayOfWeek dayOfWeek;
		private int periodNumber;
		private UUID subjectId;
		private String subjectName;
		private String subjectCode;
		private UUID teacherId;
		private String teacherName;
		private UUID sectionId;
		private String className;
		private String section;
	}

	@Getter @AllArgsConstructor
	@Schema(description = "A timetable view: either one section's week, or one teacher's week across sections")
	public static class TimetableResponse {
		@Schema(description = "SECTION or TEACHER")
		private String scope;
		@Schema(description = "Set when scope = SECTION") private UUID sectionId;
		@Schema(description = "Set when scope = SECTION, e.g. \"Grade 5 - A (2026-27)\"") private String sectionLabel;
		@Schema(description = "Set when scope = TEACHER") private UUID teacherId;
		@Schema(description = "Set when scope = TEACHER") private String teacherName;
		private String academicYear;
		private boolean saturdayEnabled;
		private List<DayOfWeek> days;
		private List<PeriodResponse> periods;
		private List<SlotResponse> slots;
	}

	@Getter @AllArgsConstructor
	@Schema(description = "One rejected slot: the teacher is already teaching another section at that time")
	public static class ClashResponse {
		private DayOfWeek dayOfWeek;
		private int periodNumber;
		private UUID teacherId;
		private String teacherName;
		private UUID conflictingSectionId;
		private String conflictingSectionLabel;
	}

}
