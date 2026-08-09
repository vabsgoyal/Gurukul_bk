package com.gurukul.auth.controller;

import com.gurukul.auth.dto.AuthDtos.GoogleIdTokenRequest;
import com.gurukul.auth.dto.AuthDtos.LoginRequest;
import com.gurukul.auth.dto.AuthDtos.LoginResponse;
import com.gurukul.auth.service.AuthService;
import com.gurukul.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Login. Requires X-School-Id header (usernames are unique per school).")
public class AuthController {

	private final AuthService authService;

	@PostMapping("/api/v1/auth/login")
	@Operation(summary = "Log in", description = "Returns a JWT. Send it as `Authorization: Bearer <token>` on subsequent requests.")
	public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		return ApiResponse.success(authService.login(request), "Login successful");
	}

	@PostMapping("/api/v1/auth/google")
	@Operation(summary = "Log in with Google", description = "Only works for an account that registered via Google "
			+ "(or was linked afterward) at this school - see /api/v1/register/*/google.")
	public ApiResponse<LoginResponse> loginWithGoogle(@Valid @RequestBody GoogleIdTokenRequest request) {
		return ApiResponse.success(authService.loginWithGoogle(request), "Login successful");
	}

}
