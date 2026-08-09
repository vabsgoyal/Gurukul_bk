package com.gurukul.parents.controller;

import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthContext;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.ApiResponse;
import com.gurukul.parents.service.ParentService;
import com.gurukul.registration.dto.RegistrationDtos.LinkChildRequest;
import com.gurukul.registration.service.RegistrationService;
import com.gurukul.students.dto.StudentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Parent", description = "A logged-in parent's own linked children. Requires X-School-Id and Authorization headers.")
public class ParentController {

	private final ParentService parentService;
	private final RegistrationService registrationService;

	@GetMapping("/api/v1/parents/me/children")
	@Operation(summary = "List my linked children at this school")
	public ApiResponse<List<StudentResponse>> myChildren() {
		AuthPrincipal principal = requireParent(AuthContext.current());
		return ApiResponse.success(parentService.listMyChildren(principal.getOwnerId()));
	}

	@PostMapping("/api/v1/parents/me/children")
	@Operation(summary = "Link another child to my account (e.g. a sibling), by roll number - no re-approval needed")
	public ApiResponse<Void> linkChild(@Valid @RequestBody LinkChildRequest request) {
		AuthPrincipal principal = requireParent(AuthContext.current());
		registrationService.linkAdditionalChild(principal.getOwnerId(), request);
		return ApiResponse.success(null, "Child linked");
	}

	private AuthPrincipal requireParent(AuthPrincipal principal) {
		if (principal.getRole() != Role.PARENT) {
			throw new AccessDeniedException("Only a parent account can do this");
		}
		return principal;
	}

}
