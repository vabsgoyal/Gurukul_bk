package com.gurukul.ai.service;

import com.gurukul.ai.config.OpenRouterProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user sliding-window cap on AI calls. This is a cost control, not a nicety: every call spends
 * real OpenRouter credit, so without it one script with a valid student login can drain the
 * school's balance overnight.
 *
 * <p>Deliberately in-memory, which has two consequences worth knowing before this is relied on as a
 * security boundary: the window resets on restart/redeploy, and it is per-instance, so a
 * horizontally scaled deployment multiplies the effective limit by the instance count. Today's
 * single EC2 instance makes that fine. Move to Redis or a small DB table before scaling out.
 *
 * <p>The real backstop against a compromised key is the per-key credit limit set on the OpenRouter
 * key itself - this layer only stops honest over-use and casual abuse.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiRateLimiter {

	private static final long WINDOW_SECONDS = 3600;

	private final OpenRouterProperties properties;
	private final Map<UUID, Deque<Instant>> callsByUser = new ConcurrentHashMap<>();

	/** Records this call and throws if the caller is already at their hourly limit. */
	public void checkAndRecord(UUID ownerId) {
		Instant now = Instant.now();
		Instant cutoff = now.minusSeconds(WINDOW_SECONDS);
		int limit = properties.maxRequestsPerUserPerHour();

		Deque<Instant> calls = callsByUser.computeIfAbsent(ownerId, key -> new ArrayDeque<>());
		// One user's deque is only ever touched under its own lock; ConcurrentHashMap handles the
		// map itself. ArrayDeque is not thread-safe, so the same user's concurrent requests must
		// not race here - two in-flight requests from one phone is entirely normal.
		synchronized (calls) {
			while (!calls.isEmpty() && calls.peekFirst().isBefore(cutoff)) {
				calls.pollFirst();
			}
			if (calls.size() >= limit) {
				log.warn("AI rate limit hit for owner {} ({} calls in the last hour)", ownerId, calls.size());
				throw new AiUnavailableException(
						"You've reached the hourly limit for the assistant. Please try again later.");
			}
			calls.addLast(now);
		}

		// Users who stopped asking would otherwise accumulate empty deques forever. Cheap sweep,
		// only when the map has grown enough to be worth it.
		if (callsByUser.size() > 1000) {
			callsByUser.entrySet().removeIf(entry -> {
				Deque<Instant> deque = entry.getValue();
				synchronized (deque) {
					return deque.isEmpty() || deque.peekLast().isBefore(cutoff);
				}
			});
		}
	}

}
