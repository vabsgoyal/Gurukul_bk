package com.gurukul.registration.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.registration.dto.RegistrationDtos.ParentGoogleRegistrationRequest;
import com.gurukul.registration.dto.RegistrationDtos.ParentRegistrationRequest;
import com.gurukul.registration.dto.RegistrationDtos.RegistrationSubmittedResponse;
import com.gurukul.registration.dto.RegistrationDtos.StudentGoogleRegistrationRequest;
import com.gurukul.registration.dto.RegistrationDtos.StudentRegistrationRequest;
import com.gurukul.registration.dto.RegistrationDtos.TeacherGoogleRegistrationRequest;
import com.gurukul.registration.dto.RegistrationDtos.TeacherRegistrationRequest;
import com.gurukul.registration.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Registration", description = "Public self-registration for student/teacher/parent - no "
		+ "Authorization header required, but a login never works until an admin approves the request "
		+ "(see /api/v1/registrations for the approval inbox). Requires X-School-Id header - pick the "
		+ "school first via GET /api/v1/schools. Each role has a Google variant that takes an idToken "
		+ "instead of username/password.")
public class RegistrationController {

	private final RegistrationService registrationService;

	@PostMapping("/api/v1/register/student")
	@Operation(summary = "Self-register as a student")
	public ApiResponse<RegistrationSubmittedResponse> registerStudent(@Valid @RequestBody StudentRegistrationRequest request) {
		return ApiResponse.success(registrationService.registerStudent(request));
	}

	@PostMapping("/api/v1/register/student/google")
	@Operation(summary = "Self-register as a student via Google")
	public ApiResponse<RegistrationSubmittedResponse> registerStudentViaGoogle(@Valid @RequestBody StudentGoogleRegistrationRequest request) {
		return ApiResponse.success(registrationService.registerStudentViaGoogle(request));
	}

	@PostMapping("/api/v1/register/teacher")
	@Operation(summary = "Self-register as a teacher - requires an admin-issued invite code")
	public ApiResponse<RegistrationSubmittedResponse> registerTeacher(@Valid @RequestBody TeacherRegistrationRequest request) {
		return ApiResponse.success(registrationService.registerTeacher(request));
	}

	@PostMapping("/api/v1/register/teacher/google")
	@Operation(summary = "Self-register as a teacher via Google - still requires an admin-issued invite code")
	public ApiResponse<RegistrationSubmittedResponse> registerTeacherViaGoogle(@Valid @RequestBody TeacherGoogleRegistrationRequest request) {
		return ApiResponse.success(registrationService.registerTeacherViaGoogle(request));
	}

	@PostMapping("/api/v1/register/parent")
	@Operation(summary = "Self-register as a parent, linking to an existing student by roll number")
	public ApiResponse<RegistrationSubmittedResponse> registerParent(@Valid @RequestBody ParentRegistrationRequest request) {
		return ApiResponse.success(registrationService.registerParent(request));
	}

	@PostMapping("/api/v1/register/parent/google")
	@Operation(summary = "Self-register as a parent via Google, linking to an existing student by roll number")
	public ApiResponse<RegistrationSubmittedResponse> registerParentViaGoogle(@Valid @RequestBody ParentGoogleRegistrationRequest request) {
		return ApiResponse.success(registrationService.registerParentViaGoogle(request));
	}

}
