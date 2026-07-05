package com.gurukul.reports.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.expenses.events.dto.EventExpenseDtos;
import com.gurukul.finance.dto.FundSummaryResponse;
import com.gurukul.reports.dto.ReportDtos;
import com.gurukul.reports.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Fund management reports. Requires X-School-Id header.")
public class ReportController {

	private final ReportService reportService;

	@GetMapping("/fund-summary")
	@Operation(summary = "School fund summary")
	public ApiResponse<FundSummaryResponse> fundSummary() {
		return ApiResponse.success(reportService.fundSummary());
	}

	@GetMapping("/dues")
	@Operation(summary = "Overdue fee assessments")
	public ApiResponse<ReportDtos.DuesReport> dues() {
		return ApiResponse.success(reportService.duesReport());
	}

	@GetMapping("/events/{eventId}/pnl")
	@Operation(summary = "Event P&L report")
	public ApiResponse<EventExpenseDtos.EventPnlResponse> eventPnl(@PathVariable UUID eventId) {
		return ApiResponse.success(reportService.eventPnl(eventId));
	}

	@GetMapping("/sponsorships")
	@Operation(summary = "Sponsorship summary by purpose")
	public ApiResponse<List<ReportDtos.SponsorshipReportLine>> sponsorships() {
		return ApiResponse.success(reportService.sponsorshipReport());
	}

	@GetMapping("/payroll/{year}")
	@Operation(summary = "Monthly payroll totals for a year")
	public ApiResponse<ReportDtos.PayrollYearReport> payrollYear(@PathVariable int year) {
		return ApiResponse.success(reportService.payrollYearReport(year));
	}

}
