package com.gurukul.fees.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "A payment request for a fee assessment: the amount due and a UPI deep link "
		+ "that opens the student's UPI app (e.g. PhonePe) with the amount pre-filled.")
public class FeePaymentRequestResponse {

	private UUID assessmentId;
	private String studentName;

	@Schema(description = "Amount to pay — always the full remaining due, not user-editable")
	private BigDecimal amount;

	@Schema(description = "Name of the school account receiving the payment")
	private String payeeName;

	@Schema(description = "School's bank account number")
	private String accountNumber;

	@Schema(description = "School's bank IFSC code")
	private String ifsc;

	@Schema(description = "UPI deep link (upi://pay?...) to open in a UPI app such as PhonePe, with the amount pre-filled")
	private String upiUri;

	@Schema(description = "Unique reference for this payment attempt, used to reconcile the payment afterwards")
	private String referenceId;

	private Instant generatedAt;

}
