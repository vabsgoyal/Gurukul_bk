package com.gurukul.schools.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.schools.dto.SchoolRegistrationRequest;
import com.gurukul.schools.dto.SchoolResponse;
import com.gurukul.schools.service.SchoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/schools")
@RequiredArgsConstructor
@Tag(
		name = "Schools",
		description = "Multi-tenant school (organization) registration. No X-School-Id header required."
)
public class SchoolController {

	private final SchoolService schoolService;

	@PostMapping
	@Operation(
			summary = "Register school",
			description = """
					Creates a new school tenant. Returns the school UUID — use it as X-School-Id on all other API calls.
					No X-School-Id header required for this endpoint.
					"""
	)
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "School registered"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed")
	})
	public ApiResponse<SchoolResponse> register(@Valid @RequestBody SchoolRegistrationRequest request) {
		return ApiResponse.success(schoolService.register(request), "School registered");
	}

	@GetMapping("/{id}")
	@Operation(
			summary = "Get school by ID",
			description = """
					Returns school profile and live counts (students, class-sections, teachers).
					Counts are computed from related tables, not stored on the school row.
					No X-School-Id header required.
					"""
	)
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "School found"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "School not found")
	})
	public ApiResponse<SchoolResponse> getById(
			@Parameter(description = "School UUID", required = true)
			@PathVariable UUID id) {
		return ApiResponse.success(schoolService.getById(id));
	}

}
