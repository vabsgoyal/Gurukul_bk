package com.gurukul.gamification.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What kind of answer a question-bank question takes. Only MCQ is playable in Arena games
 * (challenges, battle rooms, practice), which are tap-one-of-four; NUMERIC and SHORT_WORD are
 * marked automatically by QuizAnswerChecker and exist for typed-answer use. Long written answers
 * are never stored in the bank - they can't be marked automatically.
 */
@Schema(name = "QuizQuestionType")
public enum QuizQuestionType {
	/** Four options A-D, one correct. */
	MCQ,
	/** A number, matched exactly (2.50 equals 2.5). */
	NUMERIC,
	/** One or two words, matched ignoring case and extra spaces. */
	SHORT_WORD
}
