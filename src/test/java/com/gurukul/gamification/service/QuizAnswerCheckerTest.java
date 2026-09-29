package com.gurukul.gamification.service;

import com.gurukul.gamification.entity.QuizOption;
import com.gurukul.gamification.entity.QuizQuestion;
import com.gurukul.gamification.entity.QuizQuestionType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuizAnswerCheckerTest {

	private static QuizQuestion question(QuizQuestionType type, QuizOption correct, String answerText) {
		QuizQuestion q = new QuizQuestion();
		q.setQuestionType(type);
		q.setCorrectOption(correct);
		q.setAnswerText(answerText);
		return q;
	}

	@Test
	void mcqMatchesOnlyTheCorrectOption() {
		QuizQuestion q = question(QuizQuestionType.MCQ, QuizOption.C, null);
		assertThat(QuizAnswerChecker.isCorrectOption(q, QuizOption.C)).isTrue();
		assertThat(QuizAnswerChecker.isCorrectOption(q, QuizOption.A)).isFalse();
		assertThat(QuizAnswerChecker.isCorrectOption(q, null)).isFalse();
	}

	@Test
	void aTapNeverScoresOnANonMcqQuestion() {
		// Even if a stale correct_option were somehow present, a NUMERIC question can't be won by tapping.
		QuizQuestion q = question(QuizQuestionType.NUMERIC, QuizOption.A, "42");
		assertThat(QuizAnswerChecker.isCorrectOption(q, QuizOption.A)).isFalse();
		assertThat(QuizAnswerChecker.isCorrectText(question(QuizQuestionType.MCQ, QuizOption.A, null), "A")).isFalse();
	}

	@Test
	void numericIsExactNumericEquality() {
		QuizQuestion q = question(QuizQuestionType.NUMERIC, null, "2.5");
		assertThat(QuizAnswerChecker.isCorrectText(q, "2.5")).isTrue();
		assertThat(QuizAnswerChecker.isCorrectText(q, " 2.50 ")).isTrue();
		assertThat(QuizAnswerChecker.isCorrectText(q, "2.51")).isFalse();
		assertThat(QuizAnswerChecker.isCorrectText(q, "2.5 cm")).isFalse();
		assertThat(QuizAnswerChecker.isCorrectText(q, "")).isFalse();
		assertThat(QuizAnswerChecker.isCorrectText(q, null)).isFalse();
		assertThat(QuizAnswerChecker.numericEquals("-0", "0")).isTrue();
		assertThat(QuizAnswerChecker.numericEquals("100", "1e2")).isTrue();
		assertThat(QuizAnswerChecker.numericEquals("3.14", "3.1416")).as("no tolerance").isFalse();
	}

	@Test
	void shortWordIgnoresCaseAndExtraSpaces() {
		QuizQuestion q = question(QuizQuestionType.SHORT_WORD, null, "New Delhi");
		assertThat(QuizAnswerChecker.isCorrectText(q, "new delhi")).isTrue();
		assertThat(QuizAnswerChecker.isCorrectText(q, "  NEW    Delhi  ")).isTrue();
		assertThat(QuizAnswerChecker.isCorrectText(q, "NewDelhi")).isFalse();
		assertThat(QuizAnswerChecker.isCorrectText(q, "Delhi")).isFalse();
		assertThat(QuizAnswerChecker.isCorrectText(q, "   ")).isFalse();
		assertThat(QuizAnswerChecker.isCorrectText(question(QuizQuestionType.SHORT_WORD, null, "प्रकाश"), " प्रकाश ")).isTrue();
	}

	@Test
	void answerKeyValidation() {
		assertThat(QuizAnswerChecker.isValidNumericAnswer("-3.75")).isTrue();
		assertThat(QuizAnswerChecker.isValidNumericAnswer("3/4")).isFalse();
		assertThat(QuizAnswerChecker.isValidNumericAnswer("5 kg")).isFalse();
		assertThat(QuizAnswerChecker.isValidNumericAnswer(null)).isFalse();
		assertThat(QuizAnswerChecker.isValidShortWordAnswer("photosynthesis")).isTrue();
		assertThat(QuizAnswerChecker.isValidShortWordAnswer(" carbon   dioxide ")).isTrue();
		assertThat(QuizAnswerChecker.isValidShortWordAnswer("the carbon cycle")).isFalse();
		assertThat(QuizAnswerChecker.isValidShortWordAnswer("  ")).isFalse();
	}

}
