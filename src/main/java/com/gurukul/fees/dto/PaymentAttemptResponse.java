package com.gurukul.fees.dto;

import com.gurukul.fees.entity.PaymentAttempt;
import com.gurukul.fees.entity.PaymentAttemptStatus;
import com.gurukul.fees.entity.PaymentProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "One attempt to pay a fee assessment, via the payment gateway or the UPI-intent fallback")
public class PaymentAttemptResponse {

	private UUID id;
	private UUID assessmentId;
	private String transactionRef;
	private BigDecimal amount;
	private String currency;
	private PaymentAttemptStatus status;
	private String upiTransactionId;
	private String approvalRefNo;
	private String responseCode;
	private Instant createdAt;
	private Instant updatedAt;

	@Schema(description = "Which route this attempt used. Only RAZORPAY attempts can ever reach VERIFIED.")
	private PaymentProvider provider;

	@Schema(description = "Gateway payment id, searchable in the Razorpay dashboard; null for UPI_INTENT")
	private String razorpayPaymentId;

	@Schema(description = "Instrument actually used (upi/card/netbanking/wallet), as reported by the gateway")
	private String paymentMethod;

	@Schema(description = "Why the payment failed, when the gateway said; safe to show to the payer")
	private String failureReason;

	public static PaymentAttemptResponse from(PaymentAttempt attempt) {
		return new PaymentAttemptResponse(
				attempt.getId(),
				attempt.getAssessment().getId(),
				attempt.getTransactionRef(),
				attempt.getAmount(),
				attempt.getCurrency(),
				attempt.getStatus(),
				attempt.getUpiTransactionId(),
				attempt.getApprovalRefNo(),
				attempt.getResponseCode(),
				attempt.getCreatedAt(),
				attempt.getUpdatedAt(),
				attempt.getProvider(),
				attempt.getRazorpayPaymentId(),
				attempt.getPaymentMethod(),
				attempt.getFailureReason()
		);
	}

}
