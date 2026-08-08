package com.gurukul.teachers.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Schema(description = "Mark student attendance for a class-section/date/session")
public class MarkStudentAttendanceRequest {

	@NotNull
	@Schema(description = "Class-section UUID from GET /api/v1/class-sections")
	private UUID classSectionId;

	@NotNull
	@PastOrPresent
	@Schema(description = "Attendance date", example = "2026-07-12")
	private LocalDate attendanceDate;

	@NotBlank
	@Schema(description = "Session or period label", example = "Morning")
	private String sessionName;

	@NotEmpty
	@Valid
	@Schema(description = "Student attendance entries")
	private List<StudentAttendanceEntryRequest> entries;

}
