package com.gurukul.attendance.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Attendance status for a student on a given day")
public enum AttendanceStatus {

	@Schema(description = "Present for the full day")
	PRESENT,

	@Schema(description = "Absent for the full day")
	ABSENT,

	@Schema(description = "Arrived late")
	LATE,

	@Schema(description = "Present for only half the day")
	HALF_DAY

}
