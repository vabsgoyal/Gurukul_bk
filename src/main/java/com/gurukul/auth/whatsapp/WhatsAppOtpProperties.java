package com.gurukul.auth.whatsapp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config for the self-hosted WA-AKG WhatsApp gateway (https://github.com/mrifqidaffaaditya/WA-AKG)
 * that delivers real OTP codes. WA-AKG wraps Baileys, an unofficial WhatsApp Web client - accepted
 * as a known risk for this project rather than the official WhatsApp Business API or SMS.
 *
 * <p>baseUrl/token have no real default (see application.properties) - a blank token must fail
 * loudly at send time (OtpService logs and surfaces a friendly error), never silently "work".
 * Same fail-open-at-call-time pattern as OpenRouterProperties.
 */
@ConfigurationProperties(prefix = "app.whatsapp-otp")
public record WhatsAppOtpProperties(
		String baseUrl,
		String token,
		String senderSession,
		int timeoutSeconds,
		int codeLength,
		int expiryMinutes) {

	public boolean isConfigured() {
		return baseUrl != null && !baseUrl.isBlank() && token != null && !token.isBlank();
	}

}
