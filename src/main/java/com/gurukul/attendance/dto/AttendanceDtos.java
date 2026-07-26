package com.gurukul.attendance.dto;

import com.gurukul.attendance.entity.AttendanceRecord;
import com.gurukul.attendance.entity.AttendanceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class AttendanceDtos {

	@Getter @Setter
	public static class AttendanceEntryRequest {
		@NotNull private UUID studentId;
		@NotNull private AttendanceStatus status;
		private String remarks;
	}

	@Getter @Setter
	@Schema(description = "teacherId is only used when the caller is an ADMIN marking attendance on behalf of a "
			+ "specific teacher; when the caller is a TEACHER it's derived from their own login and this field is ignored")
	public static class BulkAttendanceRequest {
		@NotNull private LocalDate date;
		private UUID teacherId;
		@NotEmpty @Valid private List<AttendanceEntryRequest> records;
	}

	@Getter @AllArgsConstructor
	public static class AttendanceRecordResponse {
		private UUID id;
		private UUID studentId;
		private String studentName;
		private String rollNumber;
		private UUID sectionId;
		private LocalDate attendanceDate;
		private AttendanceStatus status;
		private UUID markedByTeacherId;
		private String markedByTeacherName;
		private String remarks;
		private Instant createdAt;
		private Instant updatedAt;

		public static AttendanceRecordResponse from(AttendanceRecord record) {
			return new AttendanceRecordResponse(
					record.getId(),
					record.getStudent().getId(),
					record.getStudent().getName(),
					record.getStudent().getRollNumber(),
					record.getSection().getId(),
					record.getAttendanceDate(),
					record.getStatus(),
					record.getMarkedByTeacher().getId(),
					record.getMarkedByTeacher().getName(),
					record.getRemarks(),
					record.getCreatedAt(),
					record.getUpdatedAt()
			);
		}
	}

	@Getter @AllArgsConstructor
	@Schema(description = "Roster row for a section's attendance on a given date; status is null when not yet marked")
	public static class StudentAttendanceEntryResponse {
		private UUID studentId;
		private String rollNumber;
		private String studentName;
		private AttendanceStatus status;
		private String remarks;
	}

	@Getter @AllArgsConstructor
	public static class SectionAttendanceResponse {
		private UUID sectionId;
		private String className;
		private String section;
		private String academicYear;
		private LocalDate date;
		private List<StudentAttendanceEntryResponse> entries;
	}

	@Getter @AllArgsConstructor
	public static class StudentAttendanceHistoryResponse {
		private UUID studentId;
		private String studentName;
		private String rollNumber;
		private LocalDate from;
		private LocalDate to;
		private long totalRecords;
		private long presentCount;
		private long absentCount;
		private long lateCount;
		private long halfDayCount;
		private List<AttendanceRecordResponse> records;
	}

}
