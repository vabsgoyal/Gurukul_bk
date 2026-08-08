package com.gurukul.teachers.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Teacher lifecycle status")
public enum TeacherStatus {
	ACTIVE,
	ON_LEAVE,
	ALUMNI,
	INACTIVE
}
