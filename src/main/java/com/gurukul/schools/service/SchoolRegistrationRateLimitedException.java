package com.gurukul.schools.service;

/** 429: this address has registered too many schools in the last hour. */
public class SchoolRegistrationRateLimitedException extends RuntimeException {

	public SchoolRegistrationRateLimitedException(String message) {
		super(message);
	}

}
