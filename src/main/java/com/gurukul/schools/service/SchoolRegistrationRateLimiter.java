package com.gurukul.schools.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-address cap on public school registration (POST /api/v1/schools), which creates a tenant
 * plus two ADMIN logins with no login of its own. Same sliding-window, in-memory approach as
 * AiRateLimiter: it resets on redeploy and is per-instance, which is fine on today's single EC2
 * instance - move it to the database before scaling out.
 */
@Component
public class SchoolRegistrationRateLimiter {

	private static final Duration WINDOW = Duration.ofHours(1);

	private final int maxPerWindow;
	private final Map<String, Deque<Instant>> registrationsByIp = new ConcurrentHashMap<>();

	public SchoolRegistrationRateLimiter(
			@Value("${app.schools.max-registrations-per-ip-per-hour:3}") int maxPerWindow) {
		this.maxPerWindow = maxPerWindow;
	}

	/** Records this attempt and throws if the address is already at its hourly limit. */
	public void checkAndRecord(String clientIp) {
		Instant now = Instant.now();
		Instant cutoff = now.minus(WINDOW);
		Deque<Instant> attempts = registrationsByIp.computeIfAbsent(clientIp, key -> new ArrayDeque<>());
		synchronized (attempts) {
			while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
				attempts.pollFirst();
			}
			if (attempts.size() >= maxPerWindow) {
				throw new SchoolRegistrationRateLimitedException(
						"Too many schools registered from this network - please try again in an hour");
			}
			attempts.addLast(now);
		}
		if (registrationsByIp.size() > 1000) {
			registrationsByIp.entrySet().removeIf(entry -> {
				Deque<Instant> deque = entry.getValue();
				synchronized (deque) {
					return deque.isEmpty() || deque.peekLast().isBefore(cutoff);
				}
			});
		}
	}

}
