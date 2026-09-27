package com.gurukul.fees.dto;

import com.gurukul.fees.entity.PaymentProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "Which payment route this deployment is currently able to offer. The client "
		+ "asks once before showing a Pay button, rather than starting a payment and discovering "
		+ "mid-flow that the gateway isn't set up.")
public class PaymentGatewayStatusResponse {

	@Schema(description = "RAZORPAY when merchant credentials are configured, otherwise UPI_INTENT "
			+ "(the unverified deep-link fallback)")
	private PaymentProvider provider;

	@Schema(description = "True when payments are server-verified end to end. False means the "
			+ "UPI-intent fallback is in use and a 'success' is only the payer's own claim.")
	private boolean verifiedPaymentsAvailable;

	public static PaymentGatewayStatusResponse razorpay() {
		return new PaymentGatewayStatusResponse(PaymentProvider.RAZORPAY, true);
	}

	public static PaymentGatewayStatusResponse upiIntentFallback() {
		return new PaymentGatewayStatusResponse(PaymentProvider.UPI_INTENT, false);
	}

}
