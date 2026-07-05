package com.gurukul.reports.service;

import com.gurukul.common.SchoolContext;
import com.gurukul.expenses.events.dto.EventExpenseDtos;
import com.gurukul.expenses.events.service.EventExpenseService;
import com.gurukul.fees.dto.FeeAssessmentResponse;
import com.gurukul.fees.entity.FeeAssessmentStatus;
import com.gurukul.fees.service.FeePaymentService;
import com.gurukul.finance.dto.FundSummaryResponse;
import com.gurukul.finance.service.LedgerService;
import com.gurukul.payroll.entity.PayrollRun;
import com.gurukul.payroll.entity.PayrollRunStatus;
import com.gurukul.payroll.repository.PayrollLineRepository;
import com.gurukul.payroll.repository.PayrollRunRepository;
import com.gurukul.reports.dto.ReportDtos;
import com.gurukul.sponsorships.entity.Sponsorship;
import com.gurukul.sponsorships.entity.SponsorshipPurpose;
import com.gurukul.sponsorships.repository.SponsorshipPaymentRepository;
import com.gurukul.sponsorships.repository.SponsorshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

	private final LedgerService ledgerService;
	private final FeePaymentService feePaymentService;
	private final EventExpenseService eventExpenseService;
	private final SponsorshipRepository sponsorshipRepository;
	private final SponsorshipPaymentRepository sponsorshipPaymentRepository;
	private final PayrollRunRepository payrollRunRepository;
	private final PayrollLineRepository payrollLineRepository;
	private final SchoolContext schoolContext;

	@Transactional(readOnly = true)
	public FundSummaryResponse fundSummary() {
		return ledgerService.getSummary();
	}

	@Transactional(readOnly = true)
	public ReportDtos.DuesReport duesReport() {
		List<FeeAssessmentResponse> overdue = feePaymentService.listAssessments(FeeAssessmentStatus.OVERDUE);
		BigDecimal total = overdue.stream()
				.map(FeeAssessmentResponse::getRemainingDue)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		return new ReportDtos.DuesReport(overdue, total);
	}

	@Transactional(readOnly = true)
	public EventExpenseDtos.EventPnlResponse eventPnl(UUID eventId) {
		return eventExpenseService.getPnl(eventId);
	}

	@Transactional(readOnly = true)
	public List<ReportDtos.SponsorshipReportLine> sponsorshipReport() {
		UUID schoolId = schoolContext.getSchoolId();
		return Arrays.stream(SponsorshipPurpose.values())
				.map(purpose -> {
					List<Sponsorship> list = sponsorshipRepository.findAllBySchoolIdAndPurpose(schoolId, purpose);
					BigDecimal pledged = list.stream().map(Sponsorship::getPledgedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
					BigDecimal received = list.stream()
							.map(s -> sponsorshipPaymentRepository.sumBySponsorshipId(s.getId()))
							.reduce(BigDecimal.ZERO, BigDecimal::add);
					return new ReportDtos.SponsorshipReportLine(purpose.name(), pledged, received);
				})
				.filter(line -> line.getTotalPledged().compareTo(BigDecimal.ZERO) > 0
						|| line.getTotalReceived().compareTo(BigDecimal.ZERO) > 0)
				.toList();
	}

	@Transactional(readOnly = true)
	public ReportDtos.PayrollYearReport payrollYearReport(int year) {
		List<PayrollRun> runs = payrollRunRepository.findAllBySchoolIdAndYear(schoolContext.getSchoolId(), year);
		List<ReportDtos.PayrollYearReport.MonthlyTotal> months = runs.stream()
				.map(run -> {
					BigDecimal total = payrollLineRepository.findAllByRunId(run.getId()).stream()
							.map(line -> line.getNet())
							.reduce(BigDecimal.ZERO, BigDecimal::add);
					return new ReportDtos.PayrollYearReport.MonthlyTotal(
							run.getMonth(), total, run.getStatus().name());
				})
				.toList();
		return new ReportDtos.PayrollYearReport(year, months);
	}

}
