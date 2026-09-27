package com.gurukul.fees.service;

import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.fees.dto.FeePaymentRequest;
import com.gurukul.fees.dto.PaymentAttemptResponse;
import com.gurukul.fees.dto.PaymentGatewayStatusResponse;
import com.gurukul.fees.dto.RazorpayOrderResponse;
import com.gurukul.fees.dto.RazorpayVerifyRequest;
import com.gurukul.fees.entity.PaymentAttempt;
import com.gurukul.fees.entity.PaymentAttemptStatus;
import com.gurukul.fees.entity.PaymentProvider;
import com.gurukul.fees.entity.StudentFeeAssessment;
import com.gurukul.fees.gateway.RazorpayClient;
import com.gurukul.fees.repository.PaymentAttemptRepository;
import com.gurukul.fees.repository.StudentFeeAssessmentRepository;
import com.gurukul.finance.entity.PaymentMethod;
import com.gurukul.schools.entity.School;
import com.gurukul.schools.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Razorpay-routed fee payment: server-created order, server-verified outcome.
 *
 * <p>The contrast with the UPI-intent path in {@link FeePaymentService#createPaymentRequest} is the
 * whole point of this class. There, the app opens a deep link, gets nothing back, and asks the user
 * whether it worked - a claim that can be neither verified nor disproved. Here, nothing the client
 * sends is trusted at all: the checkout signature is re-derived from the key secret, and even a
 * valid signature only earns a second round trip to Razorpay to ask what the payment's real status
 * is. Only "captured", for the exact amount of the attempt, marks a fee paid.
 *
 * <p>Two independent paths reach {@link #applyOutcome}: the checkout callback (fast, user is
 * watching) and the webhook (slow, but arrives even if the app was killed mid-payment). Neither is
 * redundant - without the webhook, a student who force-quits after paying has their money taken and
 * their fee left unpaid. They race by design; see the pessimistic lock in
 * PaymentAttemptRepository.findByRazorpayOrderId for how double-recording is prevented.
 */
@Service
@RequiredArgsConstructor
public class RazorpayPaymentService {

	private static final Logger log = LoggerFactory.getLogger(RazorpayPaymentService.class);

	/** Razorpay payment statuses. "authorized" means held but NOT taken - see handleCaptured. */
	private static final String STATUS_CAPTURED = "captured";
	private static final String STATUS_AUTHORIZED = "authorized";
	private static final String STATUS_FAILED = "failed";

	/**
	 * Jackson 3 (tools.jackson), which is what Spring Boot 4 ships and what its HTTP message
	 * converters speak - NOT the Jackson 2 (com.fasterxml.jackson) classes that are also on this
	 * classpath transitively. Mixing them compiles fine and then fails at runtime with an opaque
	 * "Type definition error", so keep every Jackson reference in this feature on tools.jackson.
	 *
	 * <p>Constructed locally rather than injected: this parses one externally-supplied webhook body
	 * with readTree and needs no application-specific configuration.
	 */
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final RazorpayClient razorpayClient;
	private final StudentFeeAssessmentRepository assessmentRepository;
	private final PaymentAttemptRepository paymentAttemptRepository;
	private final FeePaymentService feePaymentService;
	private final SchoolRepository schoolRepository;
	private final SchoolContext schoolContext;

	public PaymentGatewayStatusResponse gatewayStatus() {
		return razorpayClient.isConfigured()
				? PaymentGatewayStatusResponse.razorpay()
				: PaymentGatewayStatusResponse.upiIntentFallback();
	}

	/**
	 * Creates the Razorpay order and the local attempt row that mirrors it, before the payer sees a
	 * checkout sheet. The amount is read from the assessment here and never again - in particular it
	 * is not read from anything the client sends back, so a tampered client can change what it
	 * displays but not what it is charged or what gets credited.
	 */
	@Transactional
	public RazorpayOrderResponse createOrder(UUID assessmentId) {
		StudentFeeAssessment assessment = assessmentRepository
				.findByIdAndSchoolId(assessmentId, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Fee assessment not found"));
		feePaymentService.assertCanPayOrRecord(assessment);

		BigDecimal remaining = assessment.getTotalDue().subtract(assessment.getTotalPaid());
		if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalStateException("This fee assessment is already fully paid");
		}

		School school = schoolRepository.findById(schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("School not found"));

		String transactionRef = FeePaymentService.newPaymentReference();
		long amountPaise = RazorpayClient.toPaise(remaining);

		// Notes are echoed back on the webhook payload and shown in the Razorpay dashboard, which is
		// where a bursar reconciling a disputed payment will actually look. Values must be strings.
		Map<String, String> notes = new LinkedHashMap<>();
		notes.put("assessmentId", assessment.getId().toString());
		notes.put("schoolId", school.getId().toString());
		notes.put("studentName", assessment.getStudent().getName());
		notes.put("academicYear", String.valueOf(assessment.getAcademicYear()));

		RazorpayClient.RazorpayOrder order = razorpayClient.createOrder(amountPaise, transactionRef, notes);

		PaymentAttempt attempt = new PaymentAttempt();
		attempt.setSchoolId(school.getId());
		attempt.setAssessment(assessment);
		attempt.setTransactionRef(transactionRef);
		attempt.setAmount(remaining);
		attempt.setCurrency(order.currency());
		attempt.setStatus(PaymentAttemptStatus.INITIATED);
		attempt.setProvider(PaymentProvider.RAZORPAY);
		attempt.setRazorpayOrderId(order.id());
		paymentAttemptRepository.save(attempt);

		return new RazorpayOrderResponse(
				assessment.getId(),
				transactionRef,
				order.id(),
				razorpayClient.keyId(),
				amountPaise,
				remaining,
				order.currency(),
				school.getName(),
				"Fee payment - " + assessment.getStudent().getName() + " - " + assessment.getAcademicYear(),
				assessment.getStudent().getName(),
				null,
				assessment.getStudent().getParentContact());
	}

	/**
	 * Called by the client the instant Checkout hands back a result. A valid signature proves the
	 * result came from Razorpay for this exact order - it does NOT prove the money was captured, so
	 * the payment is re-fetched from Razorpay before anything is credited.
	 */
	@Transactional
	public PaymentAttemptResponse confirmFromCheckout(RazorpayVerifyRequest request) {
		if (!razorpayClient.isValidCheckoutSignature(
				request.getRazorpayOrderId(), request.getRazorpayPaymentId(), request.getRazorpaySignature())) {
			// Nothing is mutated on a bad signature - deliberately. Marking the attempt FAILED here
			// would let anyone who knows an order id kill a legitimate in-flight payment.
			log.warn("Rejected Razorpay checkout callback with an invalid signature (orderId={})",
					request.getRazorpayOrderId());
			throw new AccessDeniedException("Payment could not be verified");
		}

		PaymentAttempt attempt = paymentAttemptRepository.findByRazorpayOrderId(request.getRazorpayOrderId())
				.orElseThrow(() -> new EntityNotFoundException("Payment attempt not found"));
		if (!attempt.getSchoolId().equals(schoolContext.getSchoolId())) {
			throw new EntityNotFoundException("Payment attempt not found");
		}
		feePaymentService.assertCanPayOrRecord(attempt.getAssessment());

		attempt.setRazorpaySignature(request.getRazorpaySignature());
		RazorpayClient.RazorpayPayment payment = razorpayClient.fetchPayment(request.getRazorpayPaymentId());
		applyOutcome(attempt, payment);
		return PaymentAttemptResponse.from(attempt);
	}

	/**
	 * Razorpay's server-to-server notification. Arrives with no X-School-Id header and no principal,
	 * so the school is resolved from the attempt the order id points at, then pushed into
	 * SchoolContext for the downstream ledger write.
	 *
	 * @return false when the event refers to an order this system doesn't know about, so the caller
	 *         can still answer 200 - a non-2xx makes Razorpay retry an event that will never
	 *         succeed.
	 */
	@Transactional
	public boolean handleWebhook(String rawBody, String signature) {
		if (!razorpayClient.isValidWebhookSignature(rawBody, signature)) {
			log.warn("Rejected Razorpay webhook with an invalid or unverifiable signature");
			throw new AccessDeniedException("Invalid webhook signature");
		}

		JsonNode root;
		try {
			root = OBJECT_MAPPER.readTree(rawBody);
		} catch (tools.jackson.core.JacksonException ex) {
			// Signature already passed, so this is well-formed-but-unparseable rather than hostile.
			log.error("Razorpay webhook body passed signature verification but could not be parsed", ex);
			return false;
		}

		String event = root.path("event").asString("");
		JsonNode entity = root.path("payload").path("payment").path("entity");
		if (entity.isMissingNode() || !entity.hasNonNull("order_id")) {
			log.info("Ignoring Razorpay webhook event without a payment entity (event={})", event);
			return false;
		}

		RazorpayClient.RazorpayPayment payment = razorpayClient.toPayment(entity);
		Optional<PaymentAttempt> found = paymentAttemptRepository.findByRazorpayOrderId(payment.orderId());
		if (found.isEmpty()) {
			log.warn("Razorpay webhook for an unknown order (event={}, orderId={}) - acknowledging so it isn't retried",
					event, payment.orderId());
			return false;
		}

		PaymentAttempt attempt = found.get();
		// recordPayment resolves the school from the ThreadLocal, which nothing has populated on this
		// request. Set it from the attempt and always unset it - a leaked value would silently scope
		// a later request on this pooled thread to the wrong school.
		schoolContext.setSchoolId(attempt.getSchoolId());
		try {
			applyOutcome(attempt, payment);
		} finally {
			schoolContext.clear();
		}
		return true;
	}

	/**
	 * The single place an attempt's terminal state is decided, shared by the checkout callback and
	 * the webhook. Idempotent: whichever arrives second observes the first's VERIFIED status and
	 * does nothing.
	 */
	private void applyOutcome(PaymentAttempt attempt, RazorpayClient.RazorpayPayment payment) {
		attempt.setRazorpayPaymentId(payment.id());
		attempt.setPaymentMethod(payment.method());

		if (attempt.getStatus() == PaymentAttemptStatus.VERIFIED) {
			// Already credited by the other path. Re-saving the ids above is harmless and keeps the
			// audit trail complete whichever route observed the payment first.
			paymentAttemptRepository.save(attempt);
			return;
		}

		String status = payment.status() == null ? "" : payment.status();
		switch (status) {
			case STATUS_CAPTURED -> handleCaptured(attempt, payment);
			case STATUS_FAILED -> {
				attempt.setStatus(PaymentAttemptStatus.FAILED);
				attempt.setFailureReason(truncate(payment.errorDescription(), 500));
				paymentAttemptRepository.save(attempt);
			}
			case STATUS_AUTHORIZED -> {
				// The payer's money is held but not taken, which only happens when auto-capture is
				// off on the merchant account. Crediting the fee now would mark it paid against money
				// that may never be collected.
				log.warn("Razorpay payment {} is authorized but not captured - enable auto-capture on the "
						+ "merchant account, or this fee will never be marked paid", payment.id());
				attempt.setStatus(PaymentAttemptStatus.PENDING);
				paymentAttemptRepository.save(attempt);
			}
			default -> {
				log.info("Razorpay payment {} in non-terminal state '{}' - leaving attempt pending",
						payment.id(), status);
				attempt.setStatus(PaymentAttemptStatus.PENDING);
				paymentAttemptRepository.save(attempt);
			}
		}
	}

	private void handleCaptured(PaymentAttempt attempt, RazorpayClient.RazorpayPayment payment) {
		long expectedPaise = RazorpayClient.toPaise(attempt.getAmount());
		if (payment.amountPaise() != expectedPaise) {
			// Should be impossible - the amount is fixed on the order Razorpay itself created - so
			// treat it as a integrity failure rather than crediting the wrong number.
			log.error("Razorpay payment {} captured {} paise but attempt {} expected {} - refusing to credit",
					payment.id(), payment.amountPaise(), attempt.getTransactionRef(), expectedPaise);
			attempt.setStatus(PaymentAttemptStatus.UNKNOWN);
			attempt.setFailureReason("Captured amount did not match the amount due");
			paymentAttemptRepository.save(attempt);
			return;
		}

		attempt.setStatus(PaymentAttemptStatus.VERIFIED);
		attempt.setFailureReason(null);
		paymentAttemptRepository.save(attempt);

		FeePaymentRequest paymentRequest = new FeePaymentRequest();
		paymentRequest.setAssessmentId(attempt.getAssessment().getId());
		paymentRequest.setAmount(attempt.getAmount());
		paymentRequest.setPaymentMethod(toPaymentMethod(payment.method()));
		// The gateway's own payment id is the reference a bursar can search for in the Razorpay
		// dashboard, so it beats our internal ref on the receipt.
		paymentRequest.setPaymentReference(payment.id());
		feePaymentService.recordPayment(paymentRequest);
	}

	/**
	 * Razorpay reports the instrument actually used. Recording a card payment as UPI would put a
	 * falsehood in the ledger and on the printed receipt, so each is mapped to its own
	 * PaymentMethod; anything unrecognised falls back to BANK_TRANSFER rather than guessing.
	 */
	private static PaymentMethod toPaymentMethod(String razorpayMethod) {
		if (razorpayMethod == null) {
			return PaymentMethod.BANK_TRANSFER;
		}
		return switch (razorpayMethod) {
			case "upi" -> PaymentMethod.UPI;
			case "card", "emi" -> PaymentMethod.CARD;
			case "netbanking" -> PaymentMethod.NETBANKING;
			case "wallet" -> PaymentMethod.WALLET;
			default -> PaymentMethod.BANK_TRANSFER;
		};
	}

	private static String truncate(String value, int maxLength) {
		if (value == null || value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength);
	}

}
