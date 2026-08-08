package com.gurukul.exams.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class AssessmentResultDtos {

	@Getter @Setter
	@Schema(description = "One student's marks for the assessment being submitted")
	public static class ResultEntry {
		@NotNull private UUID studentId;
		@Schema(description = "Marks obtained - ignored (treated as null) when absent is true")
		private BigDecimal marksObtained;
		private boolean absent;
		private String remarks;
	}

	@Getter @Setter
	public static class SubmitResultsRequest {
		@NotEmpty @Valid private List<ResultEntry> results;
	}

	@Getter @AllArgsConstructor
	@Schema(description = "One roster row - marksObtained/absent/remarks are null/false until a teacher enters them")
	public static class StudentResultResponse {
		private UUID studentId;
		private String studentName;
		private String rollNumber;
		private BigDecimal marksObtained;
		private boolean absent;
		private String remarks;
	}

	@Getter @AllArgsConstructor
	public static class AssessmentResultsResponse {
		private UUID assessmentId;
		private String assessmentTitle;
		private BigDecimal maxMarks;
		private List<StudentResultResponse> results;
	}

}
