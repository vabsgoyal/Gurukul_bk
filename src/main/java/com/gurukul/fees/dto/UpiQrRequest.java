package com.gurukul.fees.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Schema(description = "Optional amount override for a UPI QR code; defaults to the assessment's remaining due")
public class UpiQrRequest {

	@DecimalMin(value = "0.01", message = "amount must be greater than 0")
	@Schema(description = "Amount to request. Omit to default to the full remaining due.", example = "5000.00")
	private BigDecimal amount;

}
