package com.gurukul.fees.entity;

/**
 * How a payment attempt was routed to the payer.
 *
 * <p>UPI_INTENT is the pre-gateway path: build a {@code upi://pay} deep link, hand off to whichever
 * UPI app is installed, and learn the outcome only from the user's own say-so. It can never produce
 * a {@link PaymentAttemptStatus#VERIFIED} attempt. RAZORPAY is server-verified end to end - see
 * RazorpayPaymentService.
 */
public enum PaymentProvider {
	UPI_INTENT,
	RAZORPAY
}
