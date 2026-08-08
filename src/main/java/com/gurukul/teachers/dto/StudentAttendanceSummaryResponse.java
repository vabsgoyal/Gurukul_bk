package com.gurukul.teachers.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "Attendance marking summary")
public class StudentAttendanceSummaryResponse {

	private UUID schoolId;
	private UUID classSectionId;
	private String classSectionLabel;
	private UUID teacherId;
	private String teacherName;
	private LocalDate attendanceDate;
	private String sessionName;
	private long presentCount;
	private long absentCount;
	private long lateCount;
	private long excusedCount;
	private List<StudentAttendanceRecordResponse> records;

	public long getTotalMarked() {
		return records.size();
	}

}
