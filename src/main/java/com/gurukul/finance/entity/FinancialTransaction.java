package com.gurukul.finance.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "financial_transaction")
public class FinancialTransaction extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TransactionDirection direction;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_type", nullable = false)
	private SourceType sourceType;

	@Column(name = "source_id", nullable = false)
	private UUID sourceId;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_method")
	private PaymentMethod paymentMethod;

	@Column(name = "payment_reference")
	private String paymentReference;

	@Column(name = "transaction_date", nullable = false)
	private LocalDate transactionDate;

	@Column(name = "receipt_number")
	private String receiptNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TransactionStatus status;

	@Column(name = "fund_account_id")
	private UUID fundAccountId;

	@Column(length = 500)
	private String notes;

}
