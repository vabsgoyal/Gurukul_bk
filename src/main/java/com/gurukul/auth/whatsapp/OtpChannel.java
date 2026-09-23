package com.gurukul.auth.whatsapp;

/**
 * How OtpService delivers a generated code to a phone. Split out from WhatsAppOtpSender (the only
 * production implementation) the same way AiProvider is split from OpenRouterAiProvider - so tests
 * can substitute a capturing fake instead of relying on the code being predictable.
 */
public interface OtpChannel {

	boolean isConfigured();

	void send(String phone, String otp);

}
