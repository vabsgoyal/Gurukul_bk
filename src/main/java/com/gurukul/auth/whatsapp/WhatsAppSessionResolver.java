package com.gurukul.auth.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Resolves which WA-AKG session sends the OTP by its human-readable name (e.g. "Smart Gurukul")
 * instead of WA-AKG's random internal session id. The id changes whenever a session is deleted and
 * recreated in the WA-AKG dashboard; looking it up by name means that never needs a backend
 * config change or restart.
 *
 * <p>The looked-up id is cached briefly so each OTP doesn't cost an extra gateway round trip, and
 * {@link #invalidate()} drops it when a send is rejected (e.g. the session was just recreated).
 * If the lookup fails or no session has that name, falls back to the configured senderSession id.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WhatsAppSessionResolver {

	static final Duration CACHE_TTL = Duration.ofMinutes(5);

	private final RestClient whatsAppOtpRestClient;
	private final WhatsAppOtpProperties properties;

	private volatile CachedSession cached;

	public String resolveSessionId() {
		CachedSession current = cached;
		if (current != null && Instant.now().isBefore(current.expiresAt())) {
			return current.sessionId();
		}

		Optional<String> byName = lookupByName();
		if (byName.isPresent()) {
			cached = new CachedSession(byName.get(), Instant.now().plus(CACHE_TTL));
			return byName.get();
		}

		String fallback = properties.senderSession();
		if (fallback == null || fallback.isBlank()) {
			throw new WhatsAppOtpDeliveryException("Could not send the OTP - please try again shortly.");
		}
		return fallback;
	}

	public void invalidate() {
		cached = null;
	}

	private Optional<String> lookupByName() {
		String name = properties.senderSessionName();
		if (name == null || name.isBlank()) {
			return Optional.empty();
		}

		SessionsResponse response;
		try {
			response = whatsAppOtpRestClient.get()
					.uri("/api/sessions")
					.retrieve()
					.body(SessionsResponse.class);
		} catch (RestClientException ex) {
			log.warn("Could not list WA-AKG sessions to resolve '{}' - falling back to configured session id", name, ex);
			return Optional.empty();
		}

		List<GatewaySession> sessions = response == null || response.data() == null ? List.of() : response.data();
		// Several sessions could share the name (e.g. an old one left behind) - prefer the one
		// that's actually connected; WA-AKG already lists newest first.
		Optional<String> match = sessions.stream()
				.filter(session -> session.name() != null && session.name().trim().equalsIgnoreCase(name.trim()))
				.sorted(Comparator.comparing((GatewaySession session) -> !"CONNECTED".equals(session.status())))
				.map(GatewaySession::sessionId)
				.findFirst();

		if (match.isEmpty()) {
			log.warn("No WA-AKG session named '{}' - falling back to configured session id", name);
		}
		return match;
	}

	private record CachedSession(String sessionId, Instant expiresAt) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record SessionsResponse(List<GatewaySession> data) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record GatewaySession(String sessionId, String name, String status) {
	}

}
