package com.gurukul.fees.service;

import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.fees.dto.FeeAssessmentResponse;
import com.gurukul.fees.dto.FeePaymentRequest;
import com.gurukul.fees.dto.FeePaymentResponse;
import com.gurukul.fees.dto.UpiQrRequest;
import com.gurukul.fees.dto.UpiQrResponse;
import com.gurukul.fees.entity.FeeAssessmentStatus;
import com.gurukul.fees.entity.FeePayment;
import com.gurukul.fees.entity.FeeUpiQrLog;
import com.gurukul.fees.entity.StudentFeeAssessment;
import com.gurukul.fees.repository.FeePaymentRepository;
import com.gurukul.fees.repository.FeeUpiQrLogRepository;
import com.gurukul.fees.repository.StudentFeeAssessmentRepository;
import com.gurukul.finance.entity.FinancialTransaction;
import com.gurukul.finance.entity.PaymentMethod;
import com.gurukul.finance.entity.SourceType;
import com.gurukul.finance.repository.FinancialTransactionRepository;
import com.gurukul.finance.service.LedgerService;
import com.gurukul.schools.entity.School;
import com.gurukul.schools.service.SchoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeePaymentService {

	private final StudentFeeAssessmentRepository assessmentRepository;
	private final FeePaymentRepository feePaymentRepository;
	private final FinancialTransactionRepository transactionRepository;
	private final FeeUpiQrLogRepository upiQrLogRepository;
	private final LedgerService ledgerService;
	private final SchoolService schoolService;
	private final SchoolContext schoolContext;

	@Transactional(readOnly = true)
	public List<FeeAssessmentResponse> listAssessments(FeeAssessmentStatus status) {
		UUID schoolId = schoolContext.getSchoolId();
		List<StudentFeeAssessment> assessments = status != null
				? assessmentRepository.findAllBySchoolIdAndStatus(schoolId, status)
				: assessmentRepository.findAllBySchoolId(schoolId);
		return assessments.stream().map(FeeAssessmentResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public List<FeeAssessmentResponse> listByStudent(UUID studentId) {
		return assessmentRepository.findAllBySchoolIdAndStudentId(schoolContext.getSchoolId(), studentId).stream()
				.map(FeeAssessmentResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public FeePaymentResponse getPayment(UUID id) {
		UUID schoolId = schoolContext.getSchoolId();
		FeePayment payment = feePaymentRepository.findByIdAndSchoolId(id, schoolId)
				.orElseThrow(() -> new EntityNotFoundException("Fee payment not found"));
		FinancialTransaction transaction = transactionRepository.findById(payment.getTransactionId())
				.orElseThrow(() -> new EntityNotFoundException("Transaction not found"));
		return FeePaymentResponse.from(payment, transaction, schoolService.getEntity(schoolId));
	}

	@Transactional
	public FeePaymentResponse recordPayment(FeePaymentRequest request) {
		StudentFeeAssessment assessment = assessmentRepository
				.findByIdAndSchoolId(request.getAssessmentId(), schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Fee assessment not found"));

		BigDecimal remaining = assessment.getTotalDue().subtract(assessment.getTotalPaid());
		if (request.getAmount().compareTo(remaining) > 0) {
			throw new IllegalArgumentException("Payment amount exceeds remaining due: " + remaining);
		}

		FeePayment payment = new FeePayment();
		payment.setSchoolId(schoolContext.getSchoolId());
		payment.setAssessment(assessment);
		payment.setAmount(request.getAmount());

		FinancialTransaction transaction = ledgerService.recordInflow(
				SourceType.FEE_PAYMENT,
				assessment.getId(),
				request.getAmount(),
				request.getPaymentMethod(),
				request.getPaymentReference(),
				request.getTransactionDate() != null ? request.getTransactionDate() : LocalDate.now(),
				null,
				"Fee payment for " + assessment.getStudent().getName(),
				assessment.getAcademicYear());

		payment.setTransactionId(transaction.getId());
		payment = feePaymentRepository.save(payment);

		assessment.setTotalPaid(assessment.getTotalPaid().add(request.getAmount()));
		assessment.setStatus(computeStatus(assessment));
		assessmentRepository.save(assessment);

		return FeePaymentResponse.from(payment, transaction, schoolService.getEntity(schoolContext.getSchoolId()));
	}

	@Transactional
	public UpiQrResponse generateUpiQr(UUID assessmentId, UpiQrRequest request) {
		UUID schoolId = schoolContext.getSchoolId();
		StudentFeeAssessment assessment = assessmentRepository.findByIdAndSchoolId(assessmentId, schoolId)
				.orElseThrow(() -> new EntityNotFoundException("Fee assessment not found"));

		School school = schoolService.getEntity(schoolId);
		if (school.getUpiVpa() == null || school.getUpiVpa().isBlank()) {
			throw new IllegalStateException(
					"UPI ID is not configured for this school. Set it in Fees → Payment Settings first.");
		}

		BigDecimal remaining = assessment.getTotalDue().subtract(assessment.getTotalPaid());
		BigDecimal amount = request.getAmount() != null ? request.getAmount() : remaining;
		if (amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Amount must be greater than 0");
		}
		if (amount.compareTo(remaining) > 0) {
			throw new IllegalArgumentException("Amount exceeds remaining due: " + remaining);
		}

		String payeeName = school.getUpiPayeeName() != null && !school.getUpiPayeeName().isBlank()
				? school.getUpiPayeeName()
				: school.getName();
		String referenceId = "FEE" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
		String note = "Fee payment - " + assessment.getStudent().getName();
		String upiUri = buildUpiUri(school.getUpiVpa(), payeeName, amount, referenceId, note);

		FeeUpiQrLog log = new FeeUpiQrLog();
		log.setSchoolId(schoolId);
		log.setAssessment(assessment);
		log.setAmount(amount);
		log.setReferenceId(referenceId);
		log.setUpiVpa(school.getUpiVpa());
		log.setPayeeName(payeeName);
		upiQrLogRepository.save(log);

		return new UpiQrResponse(
				upiUri, school.getUpiVpa(), payeeName, amount, referenceId,
				assessment.getId(), assessment.getStudent().getName(), Instant.now());
	}

	private String buildUpiUri(String vpa, String payeeName, BigDecimal amount, String referenceId, String note) {
		return "upi://pay"
				+ "?pa=" + encode(vpa)
				+ "&pn=" + encode(payeeName)
				+ "&am=" + amount.toPlainString()
				+ "&cu=INR"
				+ "&tn=" + encode(note)
				+ "&tr=" + encode(referenceId);
	}

	private String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	static FeeAssessmentStatus computeStatus(StudentFeeAssessment assessment) {
		if (assessment.getTotalPaid().compareTo(assessment.getTotalDue()) >= 0) {
			return FeeAssessmentStatus.PAID;
		}
		if (assessment.getTotalPaid().compareTo(BigDecimal.ZERO) > 0) {
			if (assessment.getDueDate() != null
					&& assessment.getDueDate().isBefore(LocalDate.now())
					&& assessment.getTotalPaid().compareTo(assessment.getTotalDue()) < 0) {
				return FeeAssessmentStatus.OVERDUE;
			}
			return FeeAssessmentStatus.PARTIAL;
		}
		if (assessment.getDueDate() != null && assessment.getDueDate().isBefore(LocalDate.now())) {
			return FeeAssessmentStatus.OVERDUE;
		}
		return FeeAssessmentStatus.UNPAID;
	}

}
