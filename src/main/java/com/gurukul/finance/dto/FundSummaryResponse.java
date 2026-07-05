package com.gurukul.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
@Schema(description = "School fund balance summary")
public class FundSummaryResponse {

	@Schema(description = "Total completed inflows")
	private BigDecimal totalInflow;

	@Schema(description = "Total completed outflows")
	private BigDecimal totalOutflow;

	@Schema(description = "Net balance (inflow - outflow)")
	private BigDecimal netBalance;

}
