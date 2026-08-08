package com.gurukul.teachers.dto;

import com.gurukul.teachers.entity.StudentAttendanceStatus;
import com.gurukul.teachers.entity.TeacherStudentAttendance;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "Student attendance record")
public class StudentAttendanceRecordResponse {

	private UUID id;
	private UUID schoolId;
	private UUID teacherId;
	private String teacherName;
	private UUID classSectionId;
	private String classSectionLabel;
	private UUID studentId;
	private String studentName;
	private String rollNumber;
	private LocalDate attendanceDate;
	private String sessionName;
	private StudentAttendanceStatus status;
	private String remarks;
	private Instant createdAt;
	private Instant updatedAt;

	public static StudentAttendanceRecordResponse from(TeacherStudentAttendance attendance) {
		return new StudentAttendanceRecordResponse(
				attendance.getId(),
				attendance.getSchoolId(),
				attendance.getTeacher().getId(),
				attendance.getTeacher().getName(),
				attendance.getClassSection().getId(),
				attendance.getClassSection().getDisplayLabel(),
				attendance.getStudent().getId(),
				attendance.getStudent().getName(),
				attendance.getStudent().getRollNumber(),
				attendance.getAttendanceDate(),
				attendance.getSessionName(),
				attendance.getStatus(),
				attendance.getRemarks(),
				attendance.getCreatedAt(),
				attendance.getUpdatedAt()
		);
	}

}
