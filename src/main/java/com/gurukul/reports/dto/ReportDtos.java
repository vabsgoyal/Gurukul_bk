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
	@Schema(description = "School-wide unpaid fees overview - unpaidAssessments covers every " +
			"UNPAID/PARTIAL/OVERDUE assessment (anything with a remaining due), overdueAssessments is " +
			"just the OVERDUE subset")
	public static class DuesReport {
		private List<FeeAssessmentResponse> unpaidAssessments;
		private BigDecimal totalUnpaid;
		private List<FeeAssessmentResponse> overdueAssessments;
		private BigDecimal totalOverdue;
	}

	@Getter @AllArgsConstructor
	@Schema(description = "How much of the school's payroll has actually been paid out vs. still pending, " +
			"across every payroll run - status is tracked per run, not per employee, so every employee " +
			"in a PAID run counts as paid and every employee in a DRAFT/PROCESSED run counts as pending")
	public static class PayrollOverview {
		private long paidEmployeeCount;
		private long pendingEmployeeCount;
		private BigDecimal paidAmount;
		private BigDecimal pendingAmount;
		private List<RunSummary> runs;

		@Getter @AllArgsConstructor
		public static class RunSummary {
			private int month;
			private int year;
			private String status;
			private long employeeCount;
			private BigDecimal totalNet;
		}
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
