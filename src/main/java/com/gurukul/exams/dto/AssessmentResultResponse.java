package com.gurukul.exams.dto;

import com.gurukul.exams.entity.AssessmentResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "A single student's recorded score on an assessment")
public class AssessmentResultResponse {

	private UUID id;
	private UUID assessmentId;
	private String assessmentTitle;
	private LocalDate assessmentDate;
	private String subjectName;
	private UUID studentId;
	private String studentName;
	private String rollNumber;
	private BigDecimal marksObtained;
	private BigDecimal maxMarks;
	private BigDecimal percentage;
	private String remarks;
	private Instant createdAt;
	private Instant updatedAt;

	public static AssessmentResultResponse from(AssessmentResult result) {
		BigDecimal maxMarks = result.getAssessment().getMaxMarks();
		BigDecimal percentage = maxMarks.compareTo(BigDecimal.ZERO) > 0
				? result.getMarksObtained().multiply(BigDecimal.valueOf(100)).divide(maxMarks, 2, RoundingMode.HALF_UP)
				: BigDecimal.ZERO;

		return new AssessmentResultResponse(
				result.getId(),
				result.getAssessment().getId(),
				result.getAssessment().getTitle(),
				result.getAssessment().getAssessmentDate(),
				result.getAssessment().getSubject() != null ? result.getAssessment().getSubject().getName() : null,
				result.getStudent().getId(),
				result.getStudent().getName(),
				result.getStudent().getRollNumber(),
				result.getMarksObtained(),
				maxMarks,
				percentage,
				result.getRemarks(),
				result.getCreatedAt(),
				result.getUpdatedAt()
		);
	}

}
