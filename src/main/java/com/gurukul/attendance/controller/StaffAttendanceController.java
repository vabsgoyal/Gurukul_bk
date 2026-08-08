package com.gurukul.attendance.controller;

import com.gurukul.attendance.dto.StaffAttendanceDtos.BulkStaffAttendanceRequest;
import com.gurukul.attendance.dto.StaffAttendanceDtos.EmployeeAttendanceHistoryResponse;
import com.gurukul.attendance.dto.StaffAttendanceDtos.SelfMarkAttendanceRequest;
import com.gurukul.attendance.dto.StaffAttendanceDtos.StaffAttendanceRecordResponse;
import com.gurukul.attendance.dto.StaffAttendanceDtos.StaffAttendanceRosterResponse;
import com.gurukul.attendance.service.StaffAttendanceService;
import com.gurukul.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Staff Attendance", description = "Daily attendance register for staff/employees. Requires X-School-Id header.")
public class StaffAttendanceController {

	private final StaffAttendanceService staffAttendanceService;

	@PostMapping("/api/v1/staff-attendance")
	@Operation(summary = "Bulk-mark staff attendance for a given date",
			description = "Marks/updates attendance for one or more staff members for one date. Calling again for the same date overwrites the prior marks.")
	public ApiResponse<StaffAttendanceRosterResponse> markStaffAttendance(@Valid @RequestBody BulkStaffAttendanceRequest request) {
		return ApiResponse.success(staffAttendanceService.markStaffAttendance(request), "Staff attendance marked");
	}

	@PostMapping("/api/v1/staff-attendance/self-mark")
	@Operation(summary = "Self-mark today's attendance from within the school's geofence",
			description = """
					Marks the caller (teacher or admin) present for today, rejecting with a 400 if the
					submitted coordinates are outside the school's configured geofence radius, or if the
					school's location hasn't been configured yet.
					""")
	public ApiResponse<StaffAttendanceRecordResponse> selfMark(@Valid @RequestBody SelfMarkAttendanceRequest request) {
		return ApiResponse.success(staffAttendanceService.selfMark(request), "Attendance marked");
	}

	@GetMapping("/api/v1/staff-attendance")
	@Operation(summary = "Get the staff attendance roster for a given date")
	public ApiResponse<StaffAttendanceRosterResponse> getStaffRoster(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return ApiResponse.success(staffAttendanceService.getStaffRoster(date));
	}

	@GetMapping("/api/v1/employees/{employeeId}/attendance")
	@Operation(summary = "Get an employee's own attendance history, optionally within a date range")
	public ApiResponse<EmployeeAttendanceHistoryResponse> getEmployeeHistory(
			@PathVariable UUID employeeId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return ApiResponse.success(staffAttendanceService.getEmployeeHistory(employeeId, from, to));
	}

}
