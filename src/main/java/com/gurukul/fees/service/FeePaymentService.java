package com.gurukul.fees.service;

import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.fees.dto.FeeAssessmentResponse;
import com.gurukul.fees.dto.FeePaymentRequest;
import com.gurukul.fees.dto.FeePaymentResponse;
import com.gurukul.fees.entity.FeeAssessmentStatus;
import com.gurukul.fees.entity.FeePayment;
import com.gurukul.fees.entity.StudentFeeAssessment;
import com.gurukul.fees.repository.FeePaymentRepository;
import com.gurukul.fees.repository.StudentFeeAssessmentRepository;
import com.gurukul.finance.entity.FinancialTransaction;
import com.gurukul.finance.entity.PaymentMethod;
import com.gurukul.finance.entity.SourceType;
import com.gurukul.finance.repository.FinancialTransactionRepository;
import com.gurukul.finance.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeePaymentService {

	private final StudentFeeAssessmentRepository assessmentRepository;
	private final FeePaymentRepository feePaymentRepository;
	private final FinancialTransactionRepository transactionRepository;
	private final LedgerService ledgerService;
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
		FeePayment payment = feePaymentRepository.findByIdAndSchoolId(id, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Fee payment not found"));
		String receiptNumber = transactionRepository.findById(payment.getTransactionId())
				.map(FinancialTransaction::getReceiptNumber)
				.orElse(null);
		return FeePaymentResponse.from(payment, receiptNumber);
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

		return FeePaymentResponse.from(payment, transaction.getReceiptNumber());
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
