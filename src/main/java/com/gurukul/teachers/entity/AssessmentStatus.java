package com.gurukul.teachers.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Teacher scheduled assessment lifecycle status")
public enum AssessmentStatus {
	DRAFT,
	SCHEDULED,
	COMPLETED,
	CANCELLED
}
