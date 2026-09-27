package com.gurukul.auth.controller;

import com.gurukul.auth.dto.AuthDtos.GoogleIdTokenRequest;
import com.gurukul.auth.dto.AuthDtos.LoginRequest;
import com.gurukul.auth.dto.AuthDtos.LoginResponse;
import com.gurukul.auth.dto.AuthDtos.RefreshTokenRequest;
import com.gurukul.auth.service.AuthService;
import com.gurukul.auth.service.SessionTokenService;
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
	private final SessionTokenService sessionTokenService;

	@PostMapping("/api/v1/auth/login")
	@Operation(summary = "Log in", description = "Returns a JWT (valid 24h) and a refresh token. Send the JWT as "
			+ "`Authorization: Bearer <token>` on subsequent requests; renew it via /api/v1/auth/refresh.")
	public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		return ApiResponse.success(authService.login(request), "Login successful");
	}

	@PostMapping("/api/v1/auth/google")
	@Operation(summary = "Log in with Google", description = "Only works for an account that registered via Google "
			+ "(or was linked afterward) at this school - see /api/v1/register/*/google.")
	public ApiResponse<LoginResponse> loginWithGoogle(@Valid @RequestBody GoogleIdTokenRequest request) {
		return ApiResponse.success(authService.loginWithGoogle(request), "Login successful");
	}

	@PostMapping("/api/v1/auth/refresh")
	@Operation(summary = "Renew a login", description = "Trades a refresh token for a new access token and a new "
			+ "refresh token (the old one stops working). No Authorization or X-School-Id header needed. "
			+ "401 means the session is over - send the user to the login screen.")
	public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
		return ApiResponse.success(sessionTokenService.refresh(request.getRefreshToken()), "Session refreshed");
	}

	@PostMapping("/api/v1/auth/logout")
	@Operation(summary = "Log out this device", description = "Revokes the refresh token. Always succeeds, even "
			+ "for an unknown or expired token.")
	public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
		sessionTokenService.revoke(request.getRefreshToken());
		return ApiResponse.success(null, "Logged out");
	}

}
