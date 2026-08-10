package com.gurukul.fees.dto;

import com.gurukul.fees.entity.PaymentAttempt;
import com.gurukul.fees.entity.PaymentAttemptStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "One attempt to pay a fee assessment via a UPI app")
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
				attempt.getUpdatedAt()
		);
	}

}
