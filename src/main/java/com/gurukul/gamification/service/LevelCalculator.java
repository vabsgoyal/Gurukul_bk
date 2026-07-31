package com.gurukul.gamification.service;

/**
 * Level N (starting at 1, 0 XP) requires cumulative XP of 50 * (N-1) * N to reach - a gentle
 * early curve that steepens with level, the standard shape for game leveling. Pure/stateless so
 * the curve is easy to retune later without touching persistence.
 */
public final class LevelCalculator {

	private LevelCalculator() {
	}

	public record LevelInfo(int level, long xpIntoLevel, long xpForNextLevel) {
	}

	public static LevelInfo forTotalXp(long totalXp) {
		int level = 1;
		while (cumulativeXpFor(level + 1) <= totalXp) {
			level++;
		}
		long xpIntoLevel = totalXp - cumulativeXpFor(level);
		long xpForNextLevel = cumulativeXpFor(level + 1) - cumulativeXpFor(level);
		return new LevelInfo(level, xpIntoLevel, xpForNextLevel);
	}

	private static long cumulativeXpFor(int level) {
		long n = level - 1L;
		return 50L * n * (n + 1L);
	}

}
