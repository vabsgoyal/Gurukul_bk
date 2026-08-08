package com.gurukul.teachers.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Student attendance status marked by a teacher")
public enum StudentAttendanceStatus {
	PRESENT,
	ABSENT,
	LATE,
	EXCUSED
}
