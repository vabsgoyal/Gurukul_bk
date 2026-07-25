package com.gurukul.attendance.controller;

import com.gurukul.attendance.dto.AttendanceDtos.BulkAttendanceRequest;
import com.gurukul.attendance.dto.AttendanceDtos.SectionAttendanceResponse;
import com.gurukul.attendance.dto.AttendanceDtos.StudentAttendanceHistoryResponse;
import com.gurukul.attendance.service.AttendanceService;
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
@Tag(name = "Attendance", description = "Daily attendance register per class-section and per student. Requires X-School-Id header.")
public class AttendanceController {

	private final AttendanceService attendanceService;

	@PostMapping("/api/v1/class-sections/{sectionId}/attendance")
	@Operation(summary = "Bulk-mark attendance for a class-section on a given date",
			description = "Teacher marks/updates attendance for the whole section for one date. Calling again for the same date overwrites the prior marks.")
	public ApiResponse<SectionAttendanceResponse> markSection(
			@PathVariable UUID sectionId, @Valid @RequestBody BulkAttendanceRequest request) {
		return ApiResponse.success(attendanceService.markSection(sectionId, request), "Attendance marked");
	}

	@GetMapping("/api/v1/class-sections/{sectionId}/attendance")
	@Operation(summary = "Get a class-section's attendance roster for a given date")
	public ApiResponse<SectionAttendanceResponse> getSectionRoster(
			@PathVariable UUID sectionId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return ApiResponse.success(attendanceService.getSectionRoster(sectionId, date));
	}

	@GetMapping("/api/v1/students/{studentId}/attendance")
	@Operation(summary = "Get a student's own attendance history, optionally within a date range")
	public ApiResponse<StudentAttendanceHistoryResponse> getStudentHistory(
			@PathVariable UUID studentId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return ApiResponse.success(attendanceService.getStudentHistory(studentId, from, to));
	}

}
