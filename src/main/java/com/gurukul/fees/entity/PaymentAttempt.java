package com.gurukul.fees.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Records one attempt to pay a fee assessment, created BEFORE the payer is handed off (to a UPI app
 * or to Razorpay Checkout) so every attempt - including ones the user never completes - is
 * traceable. See PaymentAttemptStatus for why this is deliberately kept separate from
 * FeePayment/FeeAssessment's PAID status, and PaymentProvider for what the two routes mean.
 */
@Getter
@Setter
@Entity
@Table(name = "payment_attempt")
public class PaymentAttempt extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "assessment_id", nullable = false)
	private StudentFeeAssessment assessment;

	@Column(name = "transaction_ref", nullable = false, unique = true, length = 64)
	private String transactionRef;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false, length = 3)
	private String currency = "INR";

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PaymentAttemptStatus status = PaymentAttemptStatus.INITIATED;

	@Column(name = "upi_transaction_id")
	private String upiTransactionId;

	@Column(name = "approval_ref_no")
	private String approvalRefNo;

	@Column(name = "response_code")
	private String responseCode;

	@Column(name = "raw_response", length = 2000)
	private String rawResponse;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PaymentProvider provider = PaymentProvider.UPI_INTENT;

	/**
	 * Razorpay's order id ("order_XXXX"). Globally unique (not per-school) because the webhook
	 * carries no X-School-Id header - this is the only handle available to resolve the attempt, and
	 * through it the school. Null for UPI_INTENT attempts.
	 */
	@Column(name = "razorpay_order_id", length = 64)
	private String razorpayOrderId;

	@Column(name = "razorpay_payment_id", length = 64)
	private String razorpayPaymentId;

	/** The checkout signature we verified. Kept for audit - never re-trusted after verification. */
	@Column(name = "razorpay_signature")
	private String razorpaySignature;

	/** Instrument actually used (upi/card/netbanking/wallet), as reported by Razorpay. */
	@Column(name = "payment_method", length = 30)
	private String paymentMethod;

	@Column(name = "failure_reason", length = 500)
	private String failureReason;

}
