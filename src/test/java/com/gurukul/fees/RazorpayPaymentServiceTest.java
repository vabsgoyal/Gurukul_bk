package com.gurukul.fees;

import com.gurukul.common.MissingSchoolIdException;
import com.gurukul.common.SchoolContext;
import com.gurukul.fees.dto.FeePaymentRequest;
import com.gurukul.fees.dto.RazorpayVerifyRequest;
import com.gurukul.fees.entity.FeeAssessmentStatus;
import com.gurukul.fees.entity.PaymentAttempt;
import com.gurukul.fees.entity.PaymentAttemptStatus;
import com.gurukul.fees.entity.PaymentProvider;
import com.gurukul.fees.entity.StudentFeeAssessment;
import com.gurukul.fees.gateway.RazorpayClient;
import com.gurukul.fees.repository.PaymentAttemptRepository;
import com.gurukul.fees.repository.StudentFeeAssessmentRepository;
import com.gurukul.fees.service.FeePaymentService;
import com.gurukul.fees.service.RazorpayPaymentService;
import com.gurukul.finance.entity.PaymentMethod;
import com.gurukul.schools.repository.SchoolRepository;
import com.gurukul.students.entity.Student;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The rules that decide whether money is credited. Every test here is a way a fee could be marked
 * PAID against money that was never actually taken - or taken and never credited.
 */
class RazorpayPaymentServiceTest {

	private static final UUID SCHOOL_ID = UUID.randomUUID();
	private static final UUID ASSESSMENT_ID = UUID.randomUUID();
	private static final String ORDER_ID = "order_TEST123";
	private static final String PAYMENT_ID = "pay_TEST123";
	private static final String SIGNATURE = "valid-signature";
	private static final long AMOUNT_PAISE = 1_000_000L;

	private RazorpayClient razorpayClient;
	private PaymentAttemptRepository paymentAttemptRepository;
	private FeePaymentService feePaymentService;
	private SchoolContext schoolContext;
	private RazorpayPaymentService service;
	private PaymentAttempt attempt;

	@BeforeEach
	void setUp() {
		razorpayClient = mock(RazorpayClient.class);
		paymentAttemptRepository = mock(PaymentAttemptRepository.class);
		feePaymentService = mock(FeePaymentService.class);
		schoolContext = new SchoolContext();
		schoolContext.setSchoolId(SCHOOL_ID);

		service = new RazorpayPaymentService(
				razorpayClient,
				mock(StudentFeeAssessmentRepository.class),
				paymentAttemptRepository,
				feePaymentService,
				mock(SchoolRepository.class),
				schoolContext);

		Student student = new Student();
		student.setId(UUID.randomUUID());
		student.setName("Test Student");

		StudentFeeAssessment assessment = new StudentFeeAssessment();
		assessment.setId(ASSESSMENT_ID);
		assessment.setSchoolId(SCHOOL_ID);
		assessment.setStudent(student);
		assessment.setAcademicYear("2026-27");
		assessment.setTotalDue(new BigDecimal("10000.00"));
		assessment.setTotalPaid(BigDecimal.ZERO);
		assessment.setStatus(FeeAssessmentStatus.UNPAID);

		attempt = new PaymentAttempt();
		attempt.setSchoolId(SCHOOL_ID);
		attempt.setAssessment(assessment);
		attempt.setTransactionRef("FEETEST123456");
		attempt.setAmount(new BigDecimal("10000.00"));
		attempt.setProvider(PaymentProvider.RAZORPAY);
		attempt.setStatus(PaymentAttemptStatus.INITIATED);
		attempt.setRazorpayOrderId(ORDER_ID);

		when(paymentAttemptRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(attempt));
		when(paymentAttemptRepository.save(any(PaymentAttempt.class))).thenAnswer(i -> i.getArgument(0));
	}

	private RazorpayVerifyRequest verifyRequest() {
		RazorpayVerifyRequest request = new RazorpayVerifyRequest();
		request.setRazorpayOrderId(ORDER_ID);
		request.setRazorpayPaymentId(PAYMENT_ID);
		request.setRazorpaySignature(SIGNATURE);
		return request;
	}

	private void gatewayReports(String status, long amountPaise, String method) {
		when(razorpayClient.fetchPayment(PAYMENT_ID)).thenReturn(
				new RazorpayClient.RazorpayPayment(PAYMENT_ID, ORDER_ID, status, method, amountPaise, null));
	}

	private void signatureIsValid() {
		when(razorpayClient.isValidCheckoutSignature(ORDER_ID, PAYMENT_ID, SIGNATURE)).thenReturn(true);
	}

	@Test
	void capturedPaymentIsVerifiedAndCredited() {
		signatureIsValid();
		gatewayReports("captured", AMOUNT_PAISE, "upi");

		service.confirmFromCheckout(verifyRequest());

		assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.VERIFIED);
		assertThat(attempt.getRazorpayPaymentId()).isEqualTo(PAYMENT_ID);

		ArgumentCaptor<FeePaymentRequest> captor = ArgumentCaptor.forClass(FeePaymentRequest.class);
		verify(feePaymentService).recordPayment(captor.capture());
		assertThat(captor.getValue().getAmount()).isEqualByComparingTo("10000.00");
		assertThat(captor.getValue().getAssessmentId()).isEqualTo(ASSESSMENT_ID);
		// The gateway's payment id, not our internal ref - it's what a bursar can search for.
		assertThat(captor.getValue().getPaymentReference()).isEqualTo(PAYMENT_ID);
		assertThat(captor.getValue().getPaymentMethod()).isEqualTo(PaymentMethod.UPI);
	}

	@Test
	void recordsTheInstrumentActuallyUsedRatherThanAssumingUpi() {
		signatureIsValid();
		gatewayReports("captured", AMOUNT_PAISE, "card");

		service.confirmFromCheckout(verifyRequest());

		ArgumentCaptor<FeePaymentRequest> captor = ArgumentCaptor.forClass(FeePaymentRequest.class);
		verify(feePaymentService).recordPayment(captor.capture());
		assertThat(captor.getValue().getPaymentMethod()).isEqualTo(PaymentMethod.CARD);
	}

	@Test
	void invalidSignatureIsRejectedAndChangesNothing() {
		when(razorpayClient.isValidCheckoutSignature(ORDER_ID, PAYMENT_ID, SIGNATURE)).thenReturn(false);

		assertThatThrownBy(() -> service.confirmFromCheckout(verifyRequest()))
				.isInstanceOf(AccessDeniedException.class);

		verify(feePaymentService, never()).recordPayment(any());
		// Crucially the attempt is NOT marked failed: anyone who learns an order id could otherwise
		// kill a legitimate payment that is still in flight.
		assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.INITIATED);
		verify(razorpayClient, never()).fetchPayment(anyString());
	}

	@Test
	void aValidSignatureAloneDoesNotCreditAnything() {
		// The signature proves the message came from Razorpay for this order. It says nothing about
		// whether money moved - that requires asking the gateway.
		signatureIsValid();
		gatewayReports("created", AMOUNT_PAISE, null);

		service.confirmFromCheckout(verifyRequest());

		verify(feePaymentService, never()).recordPayment(any());
		assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.PENDING);
	}

	@Test
	void authorizedButNotCapturedIsNotCredited() {
		// Money is held, not taken - only happens with auto-capture off. Crediting here would mark a
		// fee paid against money that may never be collected.
		signatureIsValid();
		gatewayReports("authorized", AMOUNT_PAISE, "card");

		service.confirmFromCheckout(verifyRequest());

		verify(feePaymentService, never()).recordPayment(any());
		assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.PENDING);
	}

	@Test
	void failedPaymentIsRecordedAsFailedAndCreditsNothing() {
		signatureIsValid();
		when(razorpayClient.fetchPayment(PAYMENT_ID)).thenReturn(new RazorpayClient.RazorpayPayment(
				PAYMENT_ID, ORDER_ID, "failed", "card", AMOUNT_PAISE, "Card declined by issuing bank"));

		service.confirmFromCheckout(verifyRequest());

		assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);
		assertThat(attempt.getFailureReason()).isEqualTo("Card declined by issuing bank");
		verify(feePaymentService, never()).recordPayment(any());
	}

	@Test
	void capturedAmountMismatchRefusesToCredit() {
		signatureIsValid();
		gatewayReports("captured", 100L, "upi");

		service.confirmFromCheckout(verifyRequest());

		verify(feePaymentService, never()).recordPayment(any());
		assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.UNKNOWN);
		assertThat(attempt.getFailureReason()).contains("did not match");
	}

	@Test
	void webhookArrivingAfterTheCallbackDoesNotCreditTwice() {
		signatureIsValid();
		gatewayReports("captured", AMOUNT_PAISE, "upi");
		service.confirmFromCheckout(verifyRequest());

		// Same payment, now via the webhook - the everyday case, since both fire for every payment.
		when(razorpayClient.isValidWebhookSignature(anyString(), anyString())).thenReturn(true);
		when(razorpayClient.toPayment(any())).thenReturn(
				new RazorpayClient.RazorpayPayment(PAYMENT_ID, ORDER_ID, "captured", "upi", AMOUNT_PAISE, null));

		service.handleWebhook(capturedWebhookBody(), "sig");

		verify(feePaymentService, times(1)).recordPayment(any());
		assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.VERIFIED);
	}

	@Test
	void webhookCreditsWhenTheCallbackNeverArrives() {
		// The reason the webhook exists: the student paid, then the app died before it could confirm.
		when(razorpayClient.isValidWebhookSignature(anyString(), anyString())).thenReturn(true);
		when(razorpayClient.toPayment(any())).thenReturn(
				new RazorpayClient.RazorpayPayment(PAYMENT_ID, ORDER_ID, "captured", "upi", AMOUNT_PAISE, null));

		boolean handled = service.handleWebhook(capturedWebhookBody(), "sig");

		assertThat(handled).isTrue();
		assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.VERIFIED);
		verify(feePaymentService).recordPayment(any());
	}

	@Test
	void webhookResolvesTheSchoolItselfAndLeavesNoContextBehind() {
		// Arrives with no X-School-Id header at all, on a pooled request thread.
		schoolContext.clear();
		when(razorpayClient.isValidWebhookSignature(anyString(), anyString())).thenReturn(true);
		when(razorpayClient.toPayment(any())).thenReturn(
				new RazorpayClient.RazorpayPayment(PAYMENT_ID, ORDER_ID, "captured", "upi", AMOUNT_PAISE, null));

		service.handleWebhook(capturedWebhookBody(), "sig");

		verify(feePaymentService).recordPayment(any());
		// A leaked school id would silently scope the next request on this thread to the wrong school.
		assertThatThrownBy(() -> schoolContext.getSchoolId()).isInstanceOf(MissingSchoolIdException.class);
	}

	@Test
	void webhookWithAnInvalidSignatureIsRejected() {
		when(razorpayClient.isValidWebhookSignature(anyString(), anyString())).thenReturn(false);

		assertThatThrownBy(() -> service.handleWebhook(capturedWebhookBody(), "bad-sig"))
				.isInstanceOf(AccessDeniedException.class);

		verify(feePaymentService, never()).recordPayment(any());
	}

	@Test
	void webhookForAnUnknownOrderIsAcknowledgedRatherThanRetriedForever() {
		when(razorpayClient.isValidWebhookSignature(anyString(), anyString())).thenReturn(true);
		when(razorpayClient.toPayment(any())).thenReturn(
				new RazorpayClient.RazorpayPayment(PAYMENT_ID, "order_UNKNOWN", "captured", "upi", AMOUNT_PAISE, null));
		when(paymentAttemptRepository.findByRazorpayOrderId("order_UNKNOWN")).thenReturn(Optional.empty());

		assertThat(service.handleWebhook(capturedWebhookBody(), "sig")).isFalse();
		verify(feePaymentService, never()).recordPayment(any());
	}

	@Test
	void webhookWithoutAPaymentEntityIsIgnored() {
		when(razorpayClient.isValidWebhookSignature(anyString(), anyString())).thenReturn(true);

		assertThat(service.handleWebhook("{\"event\":\"order.paid\",\"payload\":{}}", "sig")).isFalse();
		verify(feePaymentService, never()).recordPayment(any());
	}

	@Test
	void confirmingAnAttemptBelongingToAnotherSchoolIsNotFound() {
		signatureIsValid();
		attempt.setSchoolId(UUID.randomUUID());

		assertThatThrownBy(() -> service.confirmFromCheckout(verifyRequest()))
				.isInstanceOf(com.gurukul.common.EntityNotFoundException.class);
		verify(feePaymentService, never()).recordPayment(any());
	}

	private static String capturedWebhookBody() {
		return """
				{"event":"payment.captured","payload":{"payment":{"entity":
				{"id":"%s","order_id":"%s","status":"captured","method":"upi","amount":%d}}}}
				""".formatted(PAYMENT_ID, ORDER_ID, AMOUNT_PAISE);
	}

}
