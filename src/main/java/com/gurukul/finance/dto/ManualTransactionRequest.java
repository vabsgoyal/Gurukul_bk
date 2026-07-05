package com.gurukul.finance.dto;

import com.gurukul.finance.entity.PaymentMethod;
import com.gurukul.finance.entity.SourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Schema(description = "Request to record a manual inflow or outflow")
public class ManualTransactionRequest {

	@NotNull
	@Schema(description = "Whether this is an inflow or outflow", example = "INFLOW")
	private TransactionDirectionRequest direction;

	@NotNull
	@Schema(description = "Source type for the transaction", example = "MANUAL")
	private SourceType sourceType;

	@NotNull
	@DecimalMin(value = "0.01")
	@Schema(description = "Transaction amount", example = "1000.00")
	private BigDecimal amount;

	@Schema(description = "Payment method", example = "CASH")
	private PaymentMethod paymentMethod;

	@Schema(description = "Payment reference such as UTR or cheque number")
	private String paymentReference;

	@Schema(description = "Transaction date; defaults to today if omitted")
	private LocalDate transactionDate;

	@Schema(description = "Optional fund account UUID")
	private UUID fundAccountId;

	@Schema(description = "Optional notes")
	private String notes;

	@Schema(description = "Academic year for receipt numbering", example = "2026-27")
	private String academicYear;

	public enum TransactionDirectionRequest {
		INFLOW,
		OUTFLOW
	}

}
