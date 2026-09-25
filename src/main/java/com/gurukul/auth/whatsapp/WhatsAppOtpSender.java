package com.gurukul.auth.whatsapp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Sends the OTP text over WhatsApp via a self-hosted WA-AKG gateway
 * (https://github.com/mrifqidaffaaditya/WA-AKG). The endpoint/payload below
 * ({@code POST /api/messages/{sessionId}/{jid}/send}) were verified against a live local WA-AKG
 * instance - not just its swagger.json/README, since the "servers" base path ("/api") isn't
 * reflected in the swagger path strings themselves. Re-verify against the deployed instance's own
 * /docs if WA-AKG's API changes, since this is a third-party project this codebase does not
 * control. Auth is the {@code X-API-Key} header WA-AKG documents for server-to-server calls.
 * The session is picked by its human-readable name via {@link WhatsAppSessionResolver}, so WA-AKG
 * recreating it under a new internal id (shown in parens on its Sessions page, e.g. "3wy8uq") needs
 * no config change.
 *
 * <p>Deliberately fails soft: a WA-AKG outage or a banned/disconnected session must not take down
 * OTP login as a 500 - OtpService logs the failure and tells the caller to retry, same as
 * OpenRouterAiProvider does for its upstream.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WhatsAppOtpSender implements OtpChannel {

	private final RestClient whatsAppOtpRestClient;
	private final WhatsAppOtpProperties properties;
	private final WhatsAppSessionResolver sessionResolver;

	@Override
	public boolean isConfigured() {
		return properties.isConfigured();
	}

	@Override
	public void send(String phone, String otp) {
		String message = "Your Gurukul login code is " + otp + ". It expires in "
				+ properties.expiryMinutes() + " minutes. Do not share this code.";

		String sessionId = sessionResolver.resolveSessionId();
		try {
			whatsAppOtpRestClient.post()
					.uri("/api/messages/{sessionId}/{jid}/send", sessionId, toWhatsAppNumber(phone))
					.body(new SendMessageRequest(new TextMessage(message)))
					.retrieve()
					.toBodilessEntity();
		} catch (RestClientResponseException ex) {
			// The cached session may have been deleted/recreated - look it up again next time
			sessionResolver.invalidate();
			log.error("WA-AKG rejected OTP send for phone ending {} - status {} body {}",
					lastFourDigits(phone), ex.getStatusCode().value(), ex.getResponseBodyAsString());
			throw new WhatsAppOtpDeliveryException("Could not send the OTP - please try again shortly.");
		} catch (ResourceAccessException ex) {
			log.warn("WA-AKG unreachable or timed out sending OTP", ex);
			throw new WhatsAppOtpDeliveryException("Could not send the OTP - please try again shortly.");
		}
	}

	// WA-AKG (Baileys) expects the destination as a full international-format number, digits only,
	// with no leading "+", e.g. "917067935654" for an Indian number. Employee/Student contactPhone
	// is stored as a bare 10-digit local number with no country code (see StudentRequest/
	// EmployeeController) - every school in this deployment is in India, so 91 is prepended here
	// rather than assumed to already be present. A number already carrying a country code (11+
	// digits) is left as-is. Get this wrong and the send silently "succeeds" against a
	// nonexistent JID - Baileys accepts and queues it, but it never reaches a real device (this bit
	// us in testing: a 10-digit number with no prefix produced a 200 response that never delivered).
	private String toWhatsAppNumber(String phone) {
		String digits = phone.replaceAll("[^0-9]", "");
		if (digits.length() <= 10) {
			digits = "91" + digits;
		}
		return digits + "@s.whatsapp.net";
	}

	private String lastFourDigits(String phone) {
		return phone.length() > 4 ? phone.substring(phone.length() - 4) : phone;
	}

	private record SendMessageRequest(TextMessage message) {
	}

	private record TextMessage(String text) {
	}

}
