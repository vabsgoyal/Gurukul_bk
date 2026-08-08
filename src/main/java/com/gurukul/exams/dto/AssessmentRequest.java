package com.gurukul.exams.dto;

import com.gurukul.exams.entity.AssessmentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Schema(description = "Assessment (assignment/quiz/test/exam) create/update payload")
public class AssessmentRequest {

	@NotBlank
	@Schema(description = "Title", example = "Unit Test 1 - Algebra")
	private String title;

	@NotNull
	@Schema(description = "Assessment type", example = "TEST")
	private AssessmentType type;

	@Schema(description = "Subject UUID (optional)")
	private UUID subjectId;

	@NotNull
	@Schema(description = "Date the assessment is held", example = "2026-08-10")
	private LocalDate assessmentDate;

	@NotNull
	@DecimalMin("0.01")
	@Schema(description = "Maximum marks", example = "50")
	private BigDecimal maxMarks;

	@Schema(description = "Additional details")
	private String description;

	@Schema(description = "Teacher (Employee) UUID who scheduled this assessment (optional)")
	private UUID teacherId;

	@Schema(description = "Free-text term grouping for report cards, e.g. \"Term 1\" (optional)")
	private String term;

}
