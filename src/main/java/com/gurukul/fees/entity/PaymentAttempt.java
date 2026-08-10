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
 * Records one attempt to pay a fee assessment via a UPI app, created BEFORE the UPI app is
 * opened so every attempt (including ones the user never completes) is traceable. See
 * PaymentAttemptStatus for why this is deliberately kept separate from FeePayment/FeeAssessment's
 * PAID status.
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

}
