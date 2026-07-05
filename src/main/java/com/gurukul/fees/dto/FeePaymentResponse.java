package com.gurukul.fees.dto;

import com.gurukul.fees.entity.FeePayment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "Fee payment record")
public class FeePaymentResponse {

	private UUID id;
	private UUID schoolId;
	private UUID assessmentId;
	private UUID studentId;
	private BigDecimal amount;
	private UUID transactionId;
	private String receiptNumber;
	private Instant createdAt;
	private Instant updatedAt;

	public static FeePaymentResponse from(FeePayment payment, String receiptNumber) {
		return new FeePaymentResponse(
				payment.getId(),
				payment.getSchoolId(),
				payment.getAssessment().getId(),
				payment.getAssessment().getStudent().getId(),
				payment.getAmount(),
				payment.getTransactionId(),
				receiptNumber,
				payment.getCreatedAt(),
				payment.getUpdatedAt()
		);
	}

}
