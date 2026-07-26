package com.gurukul.attendance.dto;

import com.gurukul.attendance.entity.AttendanceStatus;
import com.gurukul.attendance.entity.StaffAttendanceRecord;
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

public class StaffAttendanceDtos {

	@Getter @Setter
	public static class StaffAttendanceEntryRequest {
		@NotNull private UUID employeeId;
		@NotNull private AttendanceStatus status;
		private String remarks;
	}

	@Getter @Setter
	public static class BulkStaffAttendanceRequest {
		@NotNull private LocalDate date;
		@NotNull private UUID markedByEmployeeId;
		@NotEmpty @Valid private List<StaffAttendanceEntryRequest> records;
	}

	@Getter @AllArgsConstructor
	public static class StaffAttendanceRecordResponse {
		private UUID id;
		private UUID employeeId;
		private String employeeName;
		private LocalDate attendanceDate;
		private AttendanceStatus status;
		private UUID markedByEmployeeId;
		private String markedByEmployeeName;
		private String remarks;
		private Instant createdAt;
		private Instant updatedAt;

		public static StaffAttendanceRecordResponse from(StaffAttendanceRecord record) {
			return new StaffAttendanceRecordResponse(
					record.getId(),
					record.getEmployee().getId(),
					record.getEmployee().getName(),
					record.getAttendanceDate(),
					record.getStatus(),
					record.getMarkedByEmployee().getId(),
					record.getMarkedByEmployee().getName(),
					record.getRemarks(),
					record.getCreatedAt(),
					record.getUpdatedAt()
			);
		}
	}

	@Getter @AllArgsConstructor
	@Schema(description = "Roster row for staff attendance on a given date; status is null when not yet marked")
	public static class StaffAttendanceEntryResponse {
		private UUID employeeId;
		private String employeeName;
		private String designation;
		private AttendanceStatus status;
		private String remarks;
	}

	@Getter @AllArgsConstructor
	public static class StaffAttendanceRosterResponse {
		private LocalDate date;
		private List<StaffAttendanceEntryResponse> entries;
	}

	@Getter @AllArgsConstructor
	public static class EmployeeAttendanceHistoryResponse {
		private UUID employeeId;
		private String employeeName;
		private LocalDate from;
		private LocalDate to;
		private long totalRecords;
		private long presentCount;
		private long absentCount;
		private long lateCount;
		private long halfDayCount;
		private List<StaffAttendanceRecordResponse> records;
	}

}
