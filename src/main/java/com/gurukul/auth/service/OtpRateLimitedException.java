package com.gurukul.auth.service;

/** Too many OTP requests for one phone - mapped to 429 so the app can show the message as is. */
public class OtpRateLimitedException extends RuntimeException {

	public OtpRateLimitedException(String message) {
		super(message);
	}

}
