package com.gurukul.teachers.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Teacher scheduled assessment type")
public enum AssessmentType {
	QUIZ,
	TEST,
	EXAM,
	ASSIGNMENT_CHECK
}
