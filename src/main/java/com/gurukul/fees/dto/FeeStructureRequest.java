package com.gurukul.fees.dto;

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
@Schema(description = "Fee structure create payload")
public class FeeStructureRequest {

	@NotNull
	@Schema(description = "Class-section UUID")
	private UUID classSectionId;

	@NotNull
	@Schema(description = "Academic year", example = "2026-27")
	private String academicYear;

	@NotEmpty
	@Valid
	@Schema(description = "Fee line items")
	private List<FeeStructureLineRequest> lines;

	@Getter
	@Setter
	public static class FeeStructureLineRequest {

		@NotNull
		@Schema(description = "Fee category UUID")
		private UUID feeCategoryId;

		@NotNull
		@DecimalMin("0.01")
		@Schema(description = "Amount for this category", example = "5000.00")
		private BigDecimal amount;

	}

}
