package com.gurukul.auth.whatsapp;

/** Thrown when the WA-AKG gateway can't be reached or rejects the send. Message is safe to show the caller. */
public class WhatsAppOtpDeliveryException extends RuntimeException {

	public WhatsAppOtpDeliveryException(String message) {
		super(message);
	}

}
