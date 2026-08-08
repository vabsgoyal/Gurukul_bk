package com.gurukul.employees.dto;

import com.gurukul.employees.entity.EmployeeFeedback;
import com.gurukul.employees.entity.FeedbackCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "Feedback record about an employee")
public class EmployeeFeedbackResponse {

	private UUID id;
	private UUID employeeId;
	private BigDecimal rating;
	private FeedbackCategory category;
	private String comment;
	private LocalDate feedbackDate;
	private String submittedBy;
	private Instant createdAt;
	private Instant updatedAt;

	public static EmployeeFeedbackResponse from(EmployeeFeedback feedback) {
		return new EmployeeFeedbackResponse(
				feedback.getId(),
				feedback.getEmployee().getId(),
				feedback.getRating(),
				feedback.getCategory(),
				feedback.getComment(),
				feedback.getFeedbackDate(),
				feedback.getSubmittedBy(),
				feedback.getCreatedAt(),
				feedback.getUpdatedAt()
		);
	}

}
