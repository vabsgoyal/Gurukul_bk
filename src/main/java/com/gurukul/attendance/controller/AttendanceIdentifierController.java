package com.gurukul.attendance.controller;

import com.gurukul.attendance.dto.AttendanceDeviceDtos.EnrollIdentifierRequest;
import com.gurukul.attendance.dto.AttendanceDeviceDtos.IdentifierResponse;
import com.gurukul.attendance.service.AttendanceIdentifierService;
import com.gurukul.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Attendance Identifiers", description = "Enrolls a student/employee's RFID/fingerprint/face identifier for device attendance. Requires X-School-Id header.")
public class AttendanceIdentifierController {

	private final AttendanceIdentifierService attendanceIdentifierService;

	@PostMapping("/api/v1/students/{studentId}/attendance-identifiers")
	public ApiResponse<IdentifierResponse> enrollForStudent(
			@PathVariable UUID studentId, @Valid @RequestBody EnrollIdentifierRequest request) {
		return ApiResponse.success(attendanceIdentifierService.enrollForStudent(studentId, request), "Identifier enrolled");
	}

	@GetMapping("/api/v1/students/{studentId}/attendance-identifiers")
	public ApiResponse<List<IdentifierResponse>> listForStudent(@PathVariable UUID studentId) {
		return ApiResponse.success(attendanceIdentifierService.listForStudent(studentId));
	}

	@PostMapping("/api/v1/employees/{employeeId}/attendance-identifiers")
	public ApiResponse<IdentifierResponse> enrollForEmployee(
			@PathVariable UUID employeeId, @Valid @RequestBody EnrollIdentifierRequest request) {
		return ApiResponse.success(attendanceIdentifierService.enrollForEmployee(employeeId, request), "Identifier enrolled");
	}

	@GetMapping("/api/v1/employees/{employeeId}/attendance-identifiers")
	public ApiResponse<List<IdentifierResponse>> listForEmployee(@PathVariable UUID employeeId) {
		return ApiResponse.success(attendanceIdentifierService.listForEmployee(employeeId));
	}

	@DeleteMapping("/api/v1/attendance-identifiers/{identifierId}")
	@Operation(summary = "Deactivate an enrolled identifier (e.g. a lost RFID card)")
	public ApiResponse<Void> remove(@PathVariable UUID identifierId) {
		attendanceIdentifierService.remove(identifierId);
		return ApiResponse.success(null, "Identifier removed");
	}

}
