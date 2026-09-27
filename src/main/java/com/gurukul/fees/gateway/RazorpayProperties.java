package com.gurukul.fees.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Razorpay merchant credentials. No defaults for the secrets - a blank secret must degrade
 * gracefully rather than silently "work", the same pattern as app.anthropic.api-key and
 * app.google.meet.client-secret. Nothing here fails at startup: callers check
 * {@link #isConfigured()} first, and the fee-payment flow falls back to the pre-gateway UPI-intent
 * path when it returns false.
 *
 * <p>keyId is NOT a secret - it is embedded in the checkout page and is meant to be public. keySecret
 * and webhookSecret are, and must never leave the server (in particular, never into an
 * EXPO_PUBLIC_* frontend variable).
 *
 * <p>Single set of credentials = a single merchant account receives every school's fees. If this
 * ever needs to be per-school (Razorpay Route linked accounts, or each school bringing its own
 * account), the only place that changes is RazorpayClient's credential lookup - everything
 * downstream already carries the schoolId.
 */
@ConfigurationProperties(prefix = "app.razorpay")
public record RazorpayProperties(
		boolean enabled,
		String baseUrl,
		String keyId,
		String keySecret,
		String webhookSecret,
		String currency,
		int timeoutSeconds) {

	/** Enough to create orders and verify checkout signatures. */
	public boolean isConfigured() {
		return enabled
				&& keyId != null && !keyId.isBlank()
				&& keySecret != null && !keySecret.isBlank();
	}

	/**
	 * Separately checked from {@link #isConfigured()}: the webhook secret is a distinct credential
	 * generated when you register the webhook URL in the dashboard, and a deployment can legitimately
	 * be mid-setup with keys configured but the webhook not yet registered. Without it, incoming
	 * webhooks are rejected rather than trusted unverified.
	 */
	public boolean isWebhookConfigured() {
		return webhookSecret != null && !webhookSecret.isBlank();
	}
}
