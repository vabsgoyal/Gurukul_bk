package com.gurukul.exams.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Schema(description = "Bulk create/update of student scores for one assessment")
public class BulkAssessmentResultRequest {

	@NotEmpty
	@Valid
	@Schema(description = "One entry per student being graded")
	private List<Entry> results;

	@Getter
	@Setter
	@Schema(description = "A single student's score entry")
	public static class Entry {

		@NotNull
		@Schema(description = "Student UUID")
		private UUID studentId;

		@NotNull
		@DecimalMin(value = "0", inclusive = true)
		@Schema(description = "Marks obtained", example = "42.5")
		private BigDecimal marksObtained;

		@Schema(description = "Optional remarks for this student's result")
		private String remarks;

	}

}
