package com.gurukul.fees.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "Generated UPI QR payload - render upiUri as a QR code for the payer to scan")
public class UpiQrResponse {

	@Schema(description = "Standard UPI deep link to encode as a QR code, e.g. upi://pay?pa=...&am=...&tr=...")
	private String upiUri;

	@Schema(description = "School's UPI VPA (payment address)")
	private String payeeVpa;

	@Schema(description = "Payee name shown in the payer's UPI app")
	private String payeeName;

	@Schema(description = "Amount requested")
	private BigDecimal amount;

	@Schema(description = "Unique reference for this QR - also usable as the payment reference when recording the payment")
	private String referenceId;

	private UUID assessmentId;
	private String studentName;

	@Schema(description = "When this QR was generated")
	private Instant generatedAt;

}
