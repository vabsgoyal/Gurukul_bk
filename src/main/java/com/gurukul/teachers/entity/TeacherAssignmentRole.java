package com.gurukul.teachers.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Teacher responsibility for a class-section")
public enum TeacherAssignmentRole {
	SUBJECT_TEACHER,
	CLASS_TEACHER,
	CO_CLASS_TEACHER
}
