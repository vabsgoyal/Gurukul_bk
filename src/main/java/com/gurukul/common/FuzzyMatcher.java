package com.gurukul.common;

import org.apache.commons.text.similarity.LevenshteinDistance;

public final class FuzzyMatcher {

	private static final double MATCH_THRESHOLD = 0.6;
	private static final LevenshteinDistance LEVENSHTEIN = LevenshteinDistance.getDefaultInstance();

	private FuzzyMatcher() {
	}

	public static boolean anyFieldMatches(String query, String... fields) {
		return bestScore(query, fields) >= MATCH_THRESHOLD;
	}

	public static double bestScore(String query, String... fields) {
		double best = 0.0;
		for (String field : fields) {
			best = Math.max(best, score(field, query));
		}
		return best;
	}

	private static double score(String field, String query) {
		if (field == null || field.isBlank() || query == null || query.isBlank()) {
			return 0.0;
		}
		String candidate = field.toLowerCase();
		String term = query.toLowerCase();
		if (candidate.equals(term)) {
			return 1.0;
		}
		if (candidate.contains(term)) {
			return 0.95;
		}
		int maxLen = Math.max(candidate.length(), term.length());
		int distance = LEVENSHTEIN.apply(candidate, term);
		return 1.0 - ((double) distance / maxLen);
	}

}
