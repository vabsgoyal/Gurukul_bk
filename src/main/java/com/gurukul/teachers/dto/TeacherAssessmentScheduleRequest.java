package com.gurukul.teachers.dto;

import com.gurukul.teachers.entity.AssessmentStatus;
import com.gurukul.teachers.entity.AssessmentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Schema(description = "Quiz/test schedule with syllabus")
public class TeacherAssessmentScheduleRequest {

	@NotNull
	@Schema(description = "Class-section UUID from GET /api/v1/class-sections")
	private UUID classSectionId;

	@NotBlank
	@Schema(description = "Subject for this quiz or test", example = "Science")
	private String subjectName;

	@NotNull
	@Schema(description = "Assessment type", example = "QUIZ")
	private AssessmentType assessmentType;

	@NotBlank
	@Schema(description = "Assessment title", example = "Photosynthesis Quiz")
	private String title;

	@NotNull
	@FutureOrPresent
	@Schema(description = "Scheduled date and time", example = "2026-08-10T10:30:00")
	private LocalDateTime scheduledAt;

	@NotBlank
	@Schema(description = "Syllabus or topics covered", example = "Chapter 4: Photosynthesis, diagrams, and short answers")
	private String syllabus;

	@NotBlank
	@Schema(description = "Student-facing instructions", example = "Bring a pencil. 20 minute quiz. No calculators.")
	private String instructions;

	@NotNull
	@Min(1)
	@Schema(description = "Maximum marks", example = "20")
	private Integer maxMarks;

	@Schema(description = "Schedule status. Defaults to SCHEDULED on create.", example = "SCHEDULED")
	private AssessmentStatus status;

}
