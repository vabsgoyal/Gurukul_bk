package com.gurukul.exams.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

public class AssessmentTermDtos {

	@Getter @AllArgsConstructor
	@Schema(description = "One distinct term string already used by assessments in a section, and whether it's been published")
	public static class TermSummaryResponse {
		private String term;
		private boolean published;
	}

	@Getter @Setter
	public static class BackfillTermRequest {
		@NotBlank
		@Schema(description = "Term to assign to every assessment in this section that currently has none", example = "Term 1")
		private String term;
	}

	@Getter @AllArgsConstructor
	public static class BackfillTermResponse {
		private int assessmentsUpdated;
	}

}
