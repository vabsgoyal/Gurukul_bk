package com.gurukul.ai;

import com.gurukul.ai.config.OpenRouterProperties;
import com.gurukul.ai.service.AiRateLimiter;
import com.gurukul.ai.service.AiUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * This limiter is a cost control, not a nicety - every allowed call spends real OpenRouter credit,
 * so an off-by-one or a race that lets calls through is a bill, not a cosmetic bug.
 */
class AiRateLimiterTest {

	private AiRateLimiter limiter(int perHour) {
		return new AiRateLimiter(new OpenRouterProperties(
				"https://example.test/v1", "k", "test/model",
				1500, 0.4, 60, "https://example.test", "Test", 20, perHour, 60000));
	}

	@Test
	void callsUpToTheLimitAreAllowedAndTheNextOneIsRejected() {
		AiRateLimiter limiter = limiter(3);
		UUID user = UUID.randomUUID();

		IntStream.range(0, 3).forEach(i ->
				assertThatCode(() -> limiter.checkAndRecord(user)).doesNotThrowAnyException());

		assertThatThrownBy(() -> limiter.checkAndRecord(user))
				.isInstanceOf(AiUnavailableException.class)
				.hasMessageContaining("hourly limit");
	}

	/** One noisy user must not be able to lock everyone else out of the feature. */
	@Test
	void theLimitIsPerUserNotGlobal() {
		AiRateLimiter limiter = limiter(2);
		UUID noisy = UUID.randomUUID();
		UUID quiet = UUID.randomUUID();

		limiter.checkAndRecord(noisy);
		limiter.checkAndRecord(noisy);
		assertThatThrownBy(() -> limiter.checkAndRecord(noisy)).isInstanceOf(AiUnavailableException.class);

		assertThatCode(() -> limiter.checkAndRecord(quiet)).doesNotThrowAnyException();
	}

	/**
	 * Two in-flight requests from one phone is entirely normal, and the per-user deque is an
	 * ArrayDeque, which is not thread-safe. Without the synchronized block this over-admits.
	 */
	@Test
	void concurrentCallsFromOneUserNeverExceedTheLimit() throws Exception {
		int limit = 20;
		int threads = 50;
		AiRateLimiter limiter = limiter(limit);
		UUID user = UUID.randomUUID();

		AtomicInteger allowed = new AtomicInteger();
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			IntStream.range(0, threads).forEach(i -> pool.submit(() -> {
				try {
					start.await();
					limiter.checkAndRecord(user);
					allowed.incrementAndGet();
				} catch (AiUnavailableException expected) {
					// over the cap - the point of the test
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} finally {
					done.countDown();
				}
			}));
			start.countDown();
			assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
		} finally {
			pool.shutdownNow();
		}

		assertThat(allowed.get()).isEqualTo(limit);
	}

	/**
	 * The map is swept once it grows past 1000 entries. The sweep must not evict a user who is
	 * still inside their window, or the cap silently resets for them.
	 */
	@Test
	void sweepingIdleUsersDoesNotResetAnActiveUsersCount() {
		AiRateLimiter limiter = limiter(2);
		UUID active = UUID.randomUUID();

		limiter.checkAndRecord(active);
		limiter.checkAndRecord(active);

		// Push the map past the sweep threshold with users who have just been recorded.
		List<UUID> others = IntStream.range(0, 1200).mapToObj(i -> UUID.randomUUID()).toList();
		others.forEach(limiter::checkAndRecord);

		assertThatThrownBy(() -> limiter.checkAndRecord(active))
				.as("active user's window must survive the sweep")
				.isInstanceOf(AiUnavailableException.class);
	}

}
