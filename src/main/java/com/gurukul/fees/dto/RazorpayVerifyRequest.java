package com.gurukul.fees.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "The three fields Razorpay Checkout hands back to the client on success. "
		+ "None of them is trusted: the signature is re-computed server-side with the key secret, "
		+ "and the payment is then re-fetched from Razorpay before anything is marked paid.")
public class RazorpayVerifyRequest {

	@NotBlank
	@Schema(example = "order_NqRxxxxxxxxxxx")
	private String razorpayOrderId;

	@NotBlank
	@Schema(example = "pay_NqRxxxxxxxxxxx")
	private String razorpayPaymentId;

	@NotBlank
	@Schema(description = "HMAC-SHA256 of \"<orderId>|<paymentId>\" keyed with the merchant key secret")
	private String razorpaySignature;

}
