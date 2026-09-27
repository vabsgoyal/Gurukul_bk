package com.gurukul.leads.service;

/** Too many demo requests from one address in the rate-limit window - mapped to 429. */
public class LeadRateLimitedException extends RuntimeException {

	public LeadRateLimitedException() {
		super("Too many requests. Please try again later, or write to sales@smartgurukul.org.");
	}

}
