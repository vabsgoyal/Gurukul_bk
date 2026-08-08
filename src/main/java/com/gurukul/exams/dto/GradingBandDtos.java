package com.gurukul.exams.dto;

import com.gurukul.exams.entity.GradingBand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

public class GradingBandDtos {

	@Getter @Setter
	@Schema(description = "One marks-percentage band, e.g. minPercentage=90, maxPercentage=100, label=\"A+\"")
	public static class GradingBandRequest {
		@NotNull private BigDecimal minPercentage;
		@NotNull private BigDecimal maxPercentage;
		@NotBlank private String label;
	}

	@Getter @AllArgsConstructor
	public static class GradingBandResponse {
		private UUID id;
		private BigDecimal minPercentage;
		private BigDecimal maxPercentage;
		private String label;

		public static GradingBandResponse from(GradingBand band) {
			return new GradingBandResponse(band.getId(), band.getMinPercentage(), band.getMaxPercentage(), band.getLabel());
		}
	}

}
