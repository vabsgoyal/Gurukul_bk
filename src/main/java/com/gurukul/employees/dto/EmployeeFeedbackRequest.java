package com.gurukul.employees.dto;

import com.gurukul.employees.entity.FeedbackCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Schema(description = "Feedback entry create/update payload")
public class EmployeeFeedbackRequest {

	@NotNull
	@DecimalMin("0")
	@DecimalMax("5")
	@Schema(description = "Rating from 0 to 5", example = "4.5")
	private BigDecimal rating;

	@NotNull
	@Schema(description = "Feedback category")
	private FeedbackCategory category;

	@Schema(description = "Optional free-text comment")
	private String comment;

	@NotNull
	@Schema(description = "Date the feedback was given", example = "2026-08-01")
	private LocalDate feedbackDate;

	@Schema(description = "Who gave this feedback", example = "Principal")
	private String submittedBy;

}
