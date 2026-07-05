package com.gurukul.fees.dto;

import com.gurukul.fees.entity.FeeAssessmentStatus;
import com.gurukul.fees.entity.StudentFeeAssessment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "Student fee assessment")
public class FeeAssessmentResponse {

	private UUID id;
	private UUID schoolId;
	private UUID studentId;
	private String studentName;
	private String rollNumber;
	private String academicYear;
	private BigDecimal totalDue;
	private BigDecimal totalPaid;
	private BigDecimal remainingDue;
	private FeeAssessmentStatus status;
	private LocalDate dueDate;
	private Instant createdAt;
	private Instant updatedAt;

	public static FeeAssessmentResponse from(StudentFeeAssessment assessment) {
		BigDecimal remaining = assessment.getTotalDue().subtract(assessment.getTotalPaid());
		return new FeeAssessmentResponse(
				assessment.getId(),
				assessment.getSchoolId(),
				assessment.getStudent().getId(),
				assessment.getStudent().getName(),
				assessment.getStudent().getRollNumber(),
				assessment.getAcademicYear(),
				assessment.getTotalDue(),
				assessment.getTotalPaid(),
				remaining,
				assessment.getStatus(),
				assessment.getDueDate(),
				assessment.getCreatedAt(),
				assessment.getUpdatedAt()
		);
	}

}
