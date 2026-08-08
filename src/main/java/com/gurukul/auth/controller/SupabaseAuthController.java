package com.gurukul.auth.controller;

import com.gurukul.auth.dto.AuthDtos.LoginResponse;
import com.gurukul.auth.security.SupabaseJwtVerifier;
import com.gurukul.auth.security.SupabaseJwtVerifier.VerifiedSupabaseToken;
import com.gurukul.auth.service.SupabaseAuthService;
import com.gurukul.common.ApiResponse;
import com.gurukul.common.SchoolContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Supabase Phone Login",
		description = "Exchanges a verified Supabase phone-OTP session for this app's own login. "
				+ "Feature-flagged via app.auth.supabase.enabled; the legacy dummy-OTP endpoints are unaffected.")
public class SupabaseAuthController {

	private final SupabaseJwtVerifier supabaseJwtVerifier;
	private final SupabaseAuthService supabaseAuthService;
	private final SchoolContext schoolContext;

	@Value("${app.auth.supabase.enabled:false}")
	private boolean enabled;

	@PostMapping("/api/v1/auth/otp/session")
	@Operation(summary = "Exchange a Supabase-verified phone session for a backend login",
			description = "Requires Authorization: Bearer <supabase-access-token> (from the client's "
					+ "supabase.auth.signInWithOtp/verifyOtp) and X-School-Id. Auto-links (or "
					+ "auto-creates, mirroring the legacy OTP flow) a Credential on first login for a "
					+ "given Supabase user. Never auto-creates ADMIN.")
	public ApiResponse<LoginResponse> exchangeSupabaseSession(
			@RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
		if (!enabled) {
			throw new AccessDeniedException("Supabase phone login is not enabled");
		}
		if (!authorizationHeader.startsWith("Bearer ")) {
			throw new BadCredentialsException("Expected 'Authorization: Bearer <supabase-access-token>'");
		}

		String token = authorizationHeader.substring(7);
		VerifiedSupabaseToken verified = supabaseJwtVerifier.verify(token);
		LoginResponse response = supabaseAuthService.login(
				verified.supabaseUserId(), verified.phone(), schoolContext.getSchoolId());
		return ApiResponse.success(response, "Login successful");
	}

}
