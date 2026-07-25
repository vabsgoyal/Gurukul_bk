package com.gurukul.exams.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Type of academic assessment")
public enum AssessmentType {

	@Schema(description = "Take-home or in-class assignment")
	ASSIGNMENT,

	@Schema(description = "Short, low-stakes quiz")
	QUIZ,

	@Schema(description = "Unit or periodic test")
	TEST,

	@Schema(description = "Term or final examination")
	EXAM

}
