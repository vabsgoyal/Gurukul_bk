package com.gurukul.fees.dto;

import com.gurukul.finance.entity.PaymentMethod;
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
@Schema(description = "Fee payment request")
public class FeePaymentRequest {

	@NotNull
	@Schema(description = "Student fee assessment UUID")
	private UUID assessmentId;

	@NotNull
	@DecimalMin("0.01")
	@Schema(description = "Payment amount", example = "1000.00")
	private BigDecimal amount;

	@NotNull
	@Schema(description = "Payment method", example = "CASH")
	private PaymentMethod paymentMethod;

	@Schema(description = "Payment reference such as UTR")
	private String paymentReference;

	@Schema(description = "Payment date; defaults to today")
	private LocalDate transactionDate;

}
