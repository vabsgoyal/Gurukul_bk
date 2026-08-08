package com.gurukul.teachers.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Generated quiz/test question type")
public enum QuestionType {
	MCQ,
	SHORT_ANSWER,
	LONG_ANSWER,
	TRUE_FALSE
}
