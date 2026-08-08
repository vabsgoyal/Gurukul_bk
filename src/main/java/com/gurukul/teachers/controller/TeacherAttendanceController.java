package com.gurukul.teachers.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.teachers.dto.MarkStudentAttendanceRequest;
import com.gurukul.teachers.dto.StudentAttendanceRecordResponse;
import com.gurukul.teachers.dto.StudentAttendanceSummaryResponse;
import com.gurukul.teachers.service.TeacherAttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teachers")
@RequiredArgsConstructor
@Tag(
		name = "Teacher Attendance",
		description = "Teacher-facing student attendance marking. Requires X-School-Id header."
)
public class TeacherAttendanceController {

	private final TeacherAttendanceService teacherAttendanceService;

	@PostMapping("/{teacherId}/attendance")
	@Operation(
			summary = "Mark student attendance",
			description = "Marks or updates attendance for students in a class-section for a date and session."
	)
	public ApiResponse<StudentAttendanceSummaryResponse> markAttendance(
			@Parameter(description = "Teacher UUID", required = true)
			@PathVariable UUID teacherId,
			@Valid @RequestBody MarkStudentAttendanceRequest request) {
		return ApiResponse.success(teacherAttendanceService.markAttendance(teacherId, request), "Attendance marked");
	}

	@GetMapping("/class-sections/{classSectionId}/attendance")
	@Operation(
			summary = "Get class-section attendance",
			description = "Returns attendance records for a class-section, date, and session."
	)
	public ApiResponse<StudentAttendanceSummaryResponse> getClassSectionAttendance(
			@Parameter(description = "Class-section UUID", required = true)
			@PathVariable UUID classSectionId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate attendanceDate,
			@RequestParam String sessionName) {
		return ApiResponse.success(teacherAttendanceService.getClassSectionAttendance(
				classSectionId, attendanceDate, sessionName));
	}

	@GetMapping("/students/{studentId}/attendance")
	@Operation(summary = "Get student attendance", description = "Returns attendance history for one student.")
	public ApiResponse<List<StudentAttendanceRecordResponse>> getStudentAttendance(
			@Parameter(description = "Student UUID", required = true)
			@PathVariable UUID studentId) {
		return ApiResponse.success(teacherAttendanceService.getStudentAttendance(studentId));
	}

}
