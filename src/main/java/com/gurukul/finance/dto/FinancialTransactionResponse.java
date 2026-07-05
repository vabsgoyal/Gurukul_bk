package com.gurukul.finance.dto;

import com.gurukul.finance.entity.FinancialTransaction;
import com.gurukul.finance.entity.PaymentMethod;
import com.gurukul.finance.entity.SourceType;
import com.gurukul.finance.entity.TransactionDirection;
import com.gurukul.finance.entity.TransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "Financial transaction record")
public class FinancialTransactionResponse {

	private UUID id;
	private UUID schoolId;
	private TransactionDirection direction;
	private SourceType sourceType;
	private UUID sourceId;
	private BigDecimal amount;
	private PaymentMethod paymentMethod;
	private String paymentReference;
	private LocalDate transactionDate;
	private String receiptNumber;
	private TransactionStatus status;
	private UUID fundAccountId;
	private String notes;
	private Instant createdAt;
	private Instant updatedAt;

	public static FinancialTransactionResponse from(FinancialTransaction transaction) {
		return new FinancialTransactionResponse(
				transaction.getId(),
				transaction.getSchoolId(),
				transaction.getDirection(),
				transaction.getSourceType(),
				transaction.getSourceId(),
				transaction.getAmount(),
				transaction.getPaymentMethod(),
				transaction.getPaymentReference(),
				transaction.getTransactionDate(),
				transaction.getReceiptNumber(),
				transaction.getStatus(),
				transaction.getFundAccountId(),
				transaction.getNotes(),
				transaction.getCreatedAt(),
				transaction.getUpdatedAt()
		);
	}

}
