package com.gurukul.gamification.service;

import com.gurukul.gamification.entity.QuizOption;
import com.gurukul.gamification.entity.QuizQuestion;
import com.gurukul.gamification.entity.QuizQuestionType;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * The one place that decides whether an answer to a question-bank question is right, and whether
 * a NUMERIC / SHORT_WORD answer key is well-formed in the first place. Shared by the Arena games
 * (MCQ taps), the bulk question-bank save, and the AI quiz generator's output validation, so the
 * three can never disagree about what "correct" means.
 *
 * <p>Marking rules:
 * <ul>
 *   <li>MCQ - the selected option equals the correct one. A non-MCQ question never scores from a
 *       tap, so a bad pool selection can't hand out free points.</li>
 *   <li>NUMERIC - exact numeric equality via BigDecimal.compareTo, so "2.50" matches "2.5" and
 *       "-0" matches "0". No tolerance: "3.14" does not match "3.1416". Thousands separators and
 *       units are not accepted - the answer key and the typed answer are both plain numbers.</li>
 *   <li>SHORT_WORD - equal after trimming, collapsing internal whitespace and lower-casing
 *       (Locale.ROOT), so "  New   Delhi " matches "new delhi".</li>
 * </ul>
 */
public final class QuizAnswerChecker {

	/** The most words a SHORT_WORD answer may have. */
	public static final int MAX_SHORT_WORD_WORDS = 2;

	private QuizAnswerChecker() {
	}

	/** Arena games: an MCQ tap. Always false for a non-MCQ question or a null selection. */
	public static boolean isCorrectOption(QuizQuestion question, QuizOption selected) {
		return question.getQuestionType() == QuizQuestionType.MCQ
				&& selected != null
				&& selected == question.getCorrectOption();
	}

	/** A typed answer to a NUMERIC or SHORT_WORD question. Always false for MCQ. */
	public static boolean isCorrectText(QuizQuestion question, String given) {
		return isCorrectText(question.getQuestionType(), question.getAnswerText(), given);
	}

	public static boolean isCorrectText(QuizQuestionType type, String expected, String given) {
		if (type == null || expected == null || given == null) {
			return false;
		}
		return switch (type) {
			case NUMERIC -> numericEquals(expected, given);
			case SHORT_WORD -> normalizeWords(expected).equals(normalizeWords(given)) && !normalizeWords(given).isEmpty();
			case MCQ -> false;
		};
	}

	public static boolean numericEquals(String expected, String given) {
		BigDecimal a = parseNumber(expected);
		BigDecimal b = parseNumber(given);
		return a != null && b != null && a.compareTo(b) == 0;
	}

	/** A plain decimal number ("42", "-3.5", "0.25", "1e3"), or null. */
	public static BigDecimal parseNumber(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		try {
			return new BigDecimal(trimmed);
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	public static boolean isValidNumericAnswer(String value) {
		return parseNumber(value) != null;
	}

	/** Trimmed, internal whitespace collapsed to one space, lower-cased. Null becomes "". */
	public static String normalizeWords(String value) {
		if (value == null) {
			return "";
		}
		return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	public static int wordCount(String value) {
		String normalized = normalizeWords(value);
		return normalized.isEmpty() ? 0 : normalized.split(" ").length;
	}

	/** One or two words. */
	public static boolean isValidShortWordAnswer(String value) {
		int words = wordCount(value);
		return words >= 1 && words <= MAX_SHORT_WORD_WORDS;
	}

}
