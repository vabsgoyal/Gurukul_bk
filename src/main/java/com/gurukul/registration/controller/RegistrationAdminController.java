package com.gurukul.registration.controller;

import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthContext;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.ApiResponse;
import com.gurukul.registration.dto.RegistrationDtos.DecisionRequest;
import com.gurukul.registration.dto.RegistrationDtos.PendingRegistrationResponse;
import com.gurukul.registration.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Registration - Admin", description = "Admin-only: the self-registration approval inbox. Student and "
		+ "teacher claims auto-activate; only parent claims land here, since roll-number/phone identity-proof is "
		+ "weaker. Requires X-School-Id and Authorization headers, admin role.")
public class RegistrationAdminController {

	private final RegistrationService registrationService;

	@GetMapping("/api/v1/registrations")
	@Operation(summary = "List pending self-registrations of one type",
			description = "entityType is PARENT_REGISTRATION - student/teacher claims auto-activate and never appear here")
	public ApiResponse<List<PendingRegistrationResponse>> listPending(@RequestParam String entityType) {
		requireAdmin(AuthContext.current());
		return ApiResponse.success(registrationService.listPending(entityType));
	}

	@PostMapping("/api/v1/registrations/{entityType}/{entityId}/approve")
	@Operation(summary = "Approve a pending self-registration - enables its login credential")
	public ApiResponse<Void> approve(
			@PathVariable String entityType, @PathVariable UUID entityId, @RequestBody(required = false) DecisionRequest request) {
		AuthPrincipal principal = requireAdmin(AuthContext.current());
		registrationService.approve(entityType, entityId, principal.getUsername(), comment(request));
		return ApiResponse.success(null, "Registration approved");
	}

	@PostMapping("/api/v1/registrations/{entityType}/{entityId}/reject")
	@Operation(summary = "Reject a pending self-registration - its login credential stays disabled")
	public ApiResponse<Void> reject(
			@PathVariable String entityType, @PathVariable UUID entityId, @RequestBody(required = false) DecisionRequest request) {
		AuthPrincipal principal = requireAdmin(AuthContext.current());
		registrationService.reject(entityType, entityId, principal.getUsername(), comment(request));
		return ApiResponse.success(null, "Registration rejected");
	}

	private AuthPrincipal requireAdmin(AuthPrincipal principal) {
		if (principal.getRole() != Role.ADMIN) {
			throw new AccessDeniedException("Only an admin can do this");
		}
		return principal;
	}

	private String comment(DecisionRequest request) {
		return request != null ? request.getComment() : null;
	}

}
