package com.gurukul.fees.gateway;

/**
 * The request was fine; the payment gateway just couldn't service it right now (unreachable,
 * timed out, or answered with an error). Distinct from IllegalArgumentException/IllegalStateException
 * so the client can tell "try again in a moment" apart from "your request was wrong" without parsing
 * prose - same shape as AiUnavailableException.
 *
 * <p>Messages on this exception are shown to students as-is, so they must never carry gateway
 * internals or anything derived from a credential.
 */
public class PaymentGatewayException extends RuntimeException {

	public static final String ERROR_CODE = "PAYMENT_GATEWAY_UNAVAILABLE";

	public PaymentGatewayException(String message) {
		super(message);
	}

	public PaymentGatewayException(String message, Throwable cause) {
		super(message, cause);
	}
}
