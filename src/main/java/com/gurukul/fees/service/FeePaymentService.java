package com.gurukul.fees.service;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthContext;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.fees.dto.FeeAssessmentResponse;
import com.gurukul.fees.dto.FeePaymentRequest;
import com.gurukul.fees.dto.FeePaymentRequestResponse;
import com.gurukul.fees.dto.FeePaymentResponse;
import com.gurukul.fees.dto.PaymentAttemptResponse;
import com.gurukul.fees.dto.PaymentAttemptResultRequest;
import com.gurukul.fees.entity.FeeAssessmentStatus;
import com.gurukul.fees.entity.FeePayment;
import com.gurukul.fees.entity.PaymentAttempt;
import com.gurukul.fees.entity.PaymentAttemptStatus;
import com.gurukul.fees.entity.StudentFeeAssessment;
import com.gurukul.fees.repository.FeePaymentRepository;
import com.gurukul.fees.repository.PaymentAttemptRepository;
import com.gurukul.fees.repository.StudentFeeAssessmentRepository;
import com.gurukul.finance.entity.FinancialTransaction;
import com.gurukul.finance.entity.PaymentMethod;
import com.gurukul.finance.entity.SourceType;
import com.gurukul.finance.repository.FinancialTransactionRepository;
import com.gurukul.finance.service.LedgerService;
import com.gurukul.parents.service.ParentService;
import com.gurukul.schools.entity.School;
import com.gurukul.schools.repository.SchoolRepository;
import com.gurukul.students.entity.ClassSection;
import com.gurukul.students.service.ClassSectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeePaymentService {

	/**
	 * Best-effort mapping from an IFSC bank code to that bank's common default UPI handle, used only
	 * to build a plausible upi:// deep link when the school hasn't set an explicit UPI ID. Several
	 * Indian banks do issue default account-linked VPAs in exactly this "<accountNumber>@<handle>"
	 * shape, but this is not guaranteed for every account - there is no payment gateway integration
	 * here to verify it, so it may fail to resolve inside the student's UPI app. See
	 * task-fee-payment.md for the full caveat.
	 */
	private static final Map<String, String> IFSC_BANK_CODE_TO_UPI_HANDLE = Map.ofEntries(
			Map.entry("SBIN", "sbi"),
			Map.entry("HDFC", "hdfcbank"),
			Map.entry("ICIC", "icici"),
			Map.entry("UTIB", "axisbank"),
			Map.entry("PUNB", "pnb"),
			Map.entry("BARB", "barodampay"),
			Map.entry("CNRB", "cnrb"),
			Map.entry("KKBK", "kotak"),
			Map.entry("YESB", "yesbank"),
			Map.entry("IDIB", "idbi"),
			Map.entry("UBIN", "unionbankofindia"),
			Map.entry("IOBA", "iob"));

	private final StudentFeeAssessmentRepository assessmentRepository;
	private final FeePaymentRepository feePaymentRepository;
	private final FinancialTransactionRepository transactionRepository;
	private final LedgerService ledgerService;
	private final SchoolContext schoolContext;
	private final ParentService parentService;
	private final SchoolRepository schoolRepository;
	private final PaymentAttemptRepository paymentAttemptRepository;
	private final ClassSectionService classSectionService;

	/**
	 * Prototype-only convenience: with no payment gateway wired up, RESPONSE_SUCCESS is the UPI
	 * app's own unverified claim (see PaymentAttemptStatus). When true, that claim is still used to
	 * mark the fee PAID so the rest of the app (receipts, dues reports) has something to show during
	 * testing. Flip to false once real server-side verification exists, so a claimed success no
	 * longer silently marks a fee as paid.
	 */
	@Value("${app.fees.unverified-upi-auto-mark-paid:true}")
	private boolean unverifiedUpiAutoMarkPaid;

	@Transactional(readOnly = true)
	public List<FeeAssessmentResponse> listAssessments(FeeAssessmentStatus status, UUID classSectionId) {
		UUID schoolId = schoolContext.getSchoolId();
		List<StudentFeeAssessment> assessments = status != null
				? assessmentRepository.findAllBySchoolIdAndStatus(schoolId, status)
				: assessmentRepository.findAllBySchoolId(schoolId);
		if (classSectionId != null) {
			assessments = assessments.stream()
					.filter(a -> a.getStudent().getClassSection() != null
							&& a.getStudent().getClassSection().getId().equals(classSectionId))
					.toList();
		}
		return assessments.stream().map(FeeAssessmentResponse::from).toList();
	}

	/**
	 * A PARENT caller must be linked to this specific student (mirrors assertCanPayOrRecord), and a
	 * STUDENT caller may only list their own assessments - both added so Parent/Student can't view an
	 * arbitrary student's fees just by guessing an id. Any other caller (EMPLOYEE, or no principal at
	 * all in tests) passes through unchanged, matching assertCanPayOrRecord's existing behavior.
	 */
	@Transactional(readOnly = true)
	public List<FeeAssessmentResponse> listByStudent(UUID studentId) {
		AuthPrincipal principal = AuthContext.currentOrNull();
		if (principal != null) {
			if (principal.getRole() == Role.PARENT) {
				parentService.requireLinkedChild(principal.getOwnerId(), studentId, principal.getSchoolId());
			} else if ((principal.getRole() == Role.STUDENT || principal.getOwnerType() == OwnerType.STUDENT)
					&& !principal.getOwnerId().equals(studentId)) {
				throw new AccessDeniedException("You can only view your own fee assessments");
			}
		}
		return assessmentRepository.findAllBySchoolIdAndStudentId(schoolContext.getSchoolId(), studentId).stream()
				.map(FeeAssessmentResponse::from)
				.toList();
	}

	/**
	 * A class teacher's own view of their section's fee dues - ADMIN may pull any section, a
	 * TEACHER only the section(s) they're the class teacher of, matching the pattern already
	 * established for report-card authority (AssessmentResultService).
	 */
	@Transactional(readOnly = true)
	public List<FeeAssessmentResponse> getClassSectionFeeStatus(UUID classSectionId) {
		ClassSection section = classSectionService.getScopedClassSection(classSectionId);
		AuthPrincipal principal = AuthContext.current();
		boolean isClassTeacher = principal.getRole() == Role.TEACHER
				&& section.getClassTeacher() != null
				&& section.getClassTeacher().getId().equals(principal.getOwnerId());
		if (principal.getRole() != Role.ADMIN && !isClassTeacher) {
			throw new AccessDeniedException("You are not the class teacher of this section");
		}
		return assessmentRepository.findAllBySchoolIdAndStudent_ClassSection_Id(schoolContext.getSchoolId(), classSectionId).stream()
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

	/**
	 * Builds the UPI deep link for a student to pay their own remaining due directly from the app,
	 * pre-filled with the exact amount owed (not user-editable). Opening the returned upiUri hands
	 * off to whichever UPI app is installed (e.g. PhonePe) - see the class-level comment on
	 * IFSC_BANK_CODE_TO_UPI_HANDLE for the honesty caveat on how the payee address is derived.
	 */
	@Transactional
	public FeePaymentRequestResponse createPaymentRequest(UUID assessmentId) {
		StudentFeeAssessment assessment = assessmentRepository
				.findByIdAndSchoolId(assessmentId, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Fee assessment not found"));
		assertCanPayOrRecord(assessment);

		BigDecimal remaining = assessment.getTotalDue().subtract(assessment.getTotalPaid());
		if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalStateException("This fee assessment is already fully paid");
		}

		School school = schoolRepository.findById(schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("School not found"));
		if (school.getBankAccountNumber() == null || school.getBankIfsc() == null) {
			throw new IllegalStateException(
					"Your school has not set up a fee payment account yet. Ask your school admin to set it up in Fee Payment Settings.");
		}

		String referenceId = "FEE" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
		String payeeName = school.getBankAccountHolderName() != null && !school.getBankAccountHolderName().isBlank()
				? school.getBankAccountHolderName()
				: school.getName();
		String payeeVpa = school.getUpiVpaOverride() != null && !school.getUpiVpaOverride().isBlank()
				? school.getUpiVpaOverride()
				: school.getBankAccountNumber() + "@"
						+ IFSC_BANK_CODE_TO_UPI_HANDLE.getOrDefault(school.getBankIfsc().substring(0, 4), "upi");

		String upiUri = "upi://pay"
				+ "?pa=" + urlEncode(payeeVpa)
				+ "&pn=" + urlEncode(payeeName)
				+ "&am=" + remaining.toPlainString()
				+ "&cu=INR"
				+ "&tr=" + urlEncode(referenceId)
				+ "&tn=" + urlEncode("Fee payment - " + assessment.getStudent().getName() + " - " + assessment.getAcademicYear());

		// Persisted before the UPI app ever opens, so every attempt - including ones the user
		// abandons or that never come back - is traceable, not just ones that happen to succeed.
		PaymentAttempt attempt = new PaymentAttempt();
		attempt.setSchoolId(schoolContext.getSchoolId());
		attempt.setAssessment(assessment);
		attempt.setTransactionRef(referenceId);
		attempt.setAmount(remaining);
		attempt.setStatus(PaymentAttemptStatus.INITIATED);
		paymentAttemptRepository.save(attempt);

		return new FeePaymentRequestResponse(
				assessment.getId(),
				assessment.getStudent().getName(),
				remaining,
				payeeName,
				school.getBankAccountNumber(),
				school.getBankIfsc(),
				payeeVpa,
				upiUri,
				referenceId,
				Instant.now());
	}

	private static String urlEncode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	/** Lets the client warn "you already have a payment in flight for this fee" before starting another. */
	@Transactional(readOnly = true)
	public Optional<PaymentAttemptResponse> findPendingAttempt(UUID assessmentId) {
		StudentFeeAssessment assessment = assessmentRepository
				.findByIdAndSchoolId(assessmentId, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Fee assessment not found"));
		assertCanPayOrRecord(assessment);
		return paymentAttemptRepository
				.findAllByAssessmentIdAndSchoolIdAndStatusIn(assessmentId, schoolContext.getSchoolId(),
						List.of(PaymentAttemptStatus.INITIATED, PaymentAttemptStatus.PENDING))
				.stream()
				.findFirst()
				.map(PaymentAttemptResponse::from);
	}

	@Transactional(readOnly = true)
	public List<PaymentAttemptResponse> listAttemptsForAssessment(UUID assessmentId) {
		StudentFeeAssessment assessment = assessmentRepository
				.findByIdAndSchoolId(assessmentId, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Fee assessment not found"));
		assertCanPayOrRecord(assessment);
		return paymentAttemptRepository
				.findAllByAssessmentIdAndSchoolIdOrderByCreatedAtDesc(assessmentId, schoolContext.getSchoolId())
				.stream()
				.map(PaymentAttemptResponse::from)
				.toList();
	}

	/**
	 * Records what the UPI app claimed (or, if it returned nothing, the user's own self-report)
	 * once control returns to this app. See PaymentAttemptStatus and unverifiedUpiAutoMarkPaid for
	 * why RESPONSE_SUCCESS here does not, by itself, prove the payment happened.
	 */
	@Transactional
	public PaymentAttemptResponse recordAttemptResult(String transactionRef, PaymentAttemptResultRequest request) {
		PaymentAttempt attempt = paymentAttemptRepository
				.findByTransactionRefAndSchoolId(transactionRef, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Payment attempt not found"));
		assertCanPayOrRecord(attempt.getAssessment());

		PaymentAttemptStatus previousStatus = attempt.getStatus();
		attempt.setStatus(request.getStatus());
		attempt.setUpiTransactionId(request.getUpiTransactionId());
		attempt.setApprovalRefNo(request.getApprovalRefNo());
		attempt.setResponseCode(request.getResponseCode());
		attempt.setRawResponse(request.getRawResponse() != null && request.getRawResponse().length() > 2000
				? request.getRawResponse().substring(0, 2000)
				: request.getRawResponse());
		attempt = paymentAttemptRepository.save(attempt);

		// Guards against double-recording a FeePayment if this endpoint is ever called twice for the
		// same attempt (e.g. a retried request) - only the first transition into RESPONSE_SUCCESS counts.
		boolean alreadyRecorded = previousStatus == PaymentAttemptStatus.RESPONSE_SUCCESS
				|| previousStatus == PaymentAttemptStatus.VERIFIED;
		if (request.getStatus() == PaymentAttemptStatus.RESPONSE_SUCCESS && unverifiedUpiAutoMarkPaid && !alreadyRecorded) {
			FeePaymentRequest paymentRequest = new FeePaymentRequest();
			paymentRequest.setAssessmentId(attempt.getAssessment().getId());
			paymentRequest.setAmount(attempt.getAmount());
			paymentRequest.setPaymentMethod(PaymentMethod.UPI);
			paymentRequest.setPaymentReference(attempt.getTransactionRef());
			recordPayment(paymentRequest);
		}

		return PaymentAttemptResponse.from(attempt);
	}

	/**
	 * A STUDENT may only pay/record for their own assessment; a PARENT must be linked to the
	 * assessment's student (mirrors listByStudent's existing check). Any other caller (EMPLOYEE, or
	 * no principal at all in tests) passes through unchanged - same pre-existing gap noted above.
	 */
	private void assertCanPayOrRecord(StudentFeeAssessment assessment) {
		AuthPrincipal principal = AuthContext.currentOrNull();
		if (principal == null) {
			return;
		}
		if (principal.getRole() == Role.STUDENT || principal.getOwnerType() == OwnerType.STUDENT) {
			if (!principal.getOwnerId().equals(assessment.getStudent().getId())) {
				throw new AccessDeniedException("You can only pay your own fees");
			}
			return;
		}
		if (principal.getRole() == Role.PARENT) {
			parentService.requireLinkedChild(principal.getOwnerId(), assessment.getStudent().getId(), principal.getSchoolId());
		}
	}

	@Transactional
	public FeePaymentResponse recordPayment(FeePaymentRequest request) {
		StudentFeeAssessment assessment = assessmentRepository
				.findByIdAndSchoolId(request.getAssessmentId(), schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Fee assessment not found"));
		assertCanPayOrRecord(assessment);

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
