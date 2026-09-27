package com.gurukul.fees.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "Everything the client needs to open Razorpay Checkout for one fee assessment. "
		+ "The amount is fixed server-side to the full remaining due - nothing the client sends back "
		+ "can change what was actually charged.")
public class RazorpayOrderResponse {

	private UUID assessmentId;

	@Schema(description = "Our own payment_attempt.transaction_ref, for reconciliation", example = "FEE7A2C91B4D3E05")
	private String transactionRef;

	@Schema(description = "Razorpay order id to hand to Checkout", example = "order_NqRxxxxxxxxxxx")
	private String orderId;

	@Schema(description = "Razorpay publishable key id. Public by design - it identifies the merchant "
			+ "on the checkout page and grants nothing on its own. Returned here rather than baked "
			+ "into the app bundle so switching test/live keys needs no app release.",
			example = "rzp_test_xxxxxxxxxxxx")
	private String keyId;

	@Schema(description = "Amount in the smallest currency unit (paise), which is what Checkout expects", example = "125050")
	private long amountPaise;

	@Schema(description = "Same amount in rupees, for display", example = "1250.50")
	private BigDecimal amount;

	private String currency;

	@Schema(description = "Merchant/school name shown on the checkout sheet")
	private String name;

	@Schema(description = "Line of context shown under the name on the checkout sheet")
	private String description;

	@Schema(description = "Prefilled payer contact details, so the student doesn't retype them; may be null")
	private String prefillName;

	private String prefillEmail;

	private String prefillContact;

}
