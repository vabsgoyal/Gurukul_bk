package com.gurukul.teachers.dto;

import com.gurukul.teachers.entity.StudentAttendanceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Schema(description = "Single student attendance entry")
public class StudentAttendanceEntryRequest {

	@NotNull
	@Schema(description = "Student UUID from the current class-section")
	private UUID studentId;

	@NotNull
	@Schema(description = "Attendance status", example = "PRESENT")
	private StudentAttendanceStatus status;

	@Schema(description = "Optional teacher remark", example = "Arrived during second period")
	private String remarks;

}
