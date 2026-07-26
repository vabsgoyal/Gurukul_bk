package com.gurukul.auth.controller;

import com.gurukul.auth.dto.AuthDtos.CredentialRequest;
import com.gurukul.auth.dto.AuthDtos.CredentialResponse;
import com.gurukul.auth.service.CredentialService;
import com.gurukul.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Credentials", description = "Provision login credentials for employees/students. Admin-only. Requires X-School-Id header.")
public class CredentialController {

	private final CredentialService credentialService;

	@PostMapping("/api/v1/employees/{employeeId}/credentials")
	@Operation(summary = "Create a login credential for an employee")
	public ApiResponse<CredentialResponse> createForEmployee(
			@PathVariable UUID employeeId, @Valid @RequestBody CredentialRequest request) {
		return ApiResponse.success(credentialService.createForEmployee(employeeId, request), "Credential created");
	}

	@PostMapping("/api/v1/students/{studentId}/credentials")
	@Operation(summary = "Create a login credential for a student")
	public ApiResponse<CredentialResponse> createForStudent(
			@PathVariable UUID studentId, @Valid @RequestBody CredentialRequest request) {
		return ApiResponse.success(credentialService.createForStudent(studentId, request), "Credential created");
	}

}
