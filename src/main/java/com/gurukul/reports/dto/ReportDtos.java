package com.gurukul.reports.dto;

import com.gurukul.expenses.events.dto.EventExpenseDtos;
import com.gurukul.fees.dto.FeeAssessmentResponse;
import com.gurukul.finance.dto.FundSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

public class ReportDtos {

	@Getter @AllArgsConstructor
	@Schema(description = "Overdue fee assessments report")
	public static class DuesReport {
		private List<FeeAssessmentResponse> overdueAssessments;
		private BigDecimal totalOverdue;
	}

	@Getter @AllArgsConstructor
	@Schema(description = "Sponsorship summary by purpose")
	public static class SponsorshipReportLine {
		private String purpose;
		private BigDecimal totalPledged;
		private BigDecimal totalReceived;
	}

	@Getter @AllArgsConstructor
	@Schema(description = "Monthly payroll totals for a year")
	public static class PayrollYearReport {
		private int year;
		private List<MonthlyTotal> months;

		@Getter @AllArgsConstructor
		public static class MonthlyTotal {
			private int month;
			private BigDecimal totalNet;
			private String status;
		}
	}

}
