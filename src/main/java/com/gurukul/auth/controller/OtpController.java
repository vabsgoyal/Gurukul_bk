package com.gurukul.auth.controller;

import com.gurukul.auth.dto.AuthDtos.LoginResponse;
import com.gurukul.auth.dto.OtpDtos.LoginProfile;
import com.gurukul.auth.dto.OtpDtos.OtpRequest;
import com.gurukul.auth.dto.OtpDtos.OtpVerifyRequest;
import com.gurukul.auth.dto.OtpDtos.OtpVerifyResponse;
import com.gurukul.auth.dto.OtpDtos.SelectProfileRequest;
import com.gurukul.auth.dto.OtpDtos.SwitchProfileRequest;
import com.gurukul.auth.security.AuthContext;
import com.gurukul.auth.service.OtpService;
import com.gurukul.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "OTP Login", description = "Phone + OTP login, code delivered over WhatsApp. Requires X-School-Id header.")
public class OtpController {

	private final OtpService otpService;

	@PostMapping("/api/v1/auth/otp/request")
	@Operation(summary = "Request an OTP",
			description = "Generates a random, single-use, time-limited code and sends it over WhatsApp. "
					+ "Errors if the phone isn't on file.")
	public ApiResponse<Void> requestOtp(@Valid @RequestBody OtpRequest request) {
		otpService.requestOtp(request.getPhone());
		return ApiResponse.success(null, "OTP sent");
	}

	@PostMapping("/api/v1/auth/otp/verify")
	@Operation(summary = "Verify an OTP and log in",
			description = "Auto-creates a login (TEACHER for an Employee's phone, STUDENT for a Student's parentContact) "
					+ "the first time a phone number verifies successfully. Never auto-creates ADMIN.")
	public ApiResponse<OtpVerifyResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
		OtpVerifyResponse response = otpService.verifyOtp(request.getPhone(), request.getOtp());
		return ApiResponse.success(response, response.isProfileSelectionRequired() ? "Choose a profile" : "Login successful");
	}

	@PostMapping("/api/v1/auth/otp/select-profile")
	@Operation(summary = "Log in as one of the profiles on a shared phone",
			description = "Finishes an OTP login that returned profileSelectionRequired. Pass the selectionToken from "
					+ "that response (valid 10 minutes) and one of its profiles.")
	public ApiResponse<LoginResponse> selectProfile(@Valid @RequestBody SelectProfileRequest request) {
		return ApiResponse.success(otpService.selectProfile(
				request.getSelectionToken(), request.getOwnerType(), request.getOwnerId()), "Login successful");
	}

	@GetMapping("/api/v1/auth/profiles")
	@Operation(summary = "Profiles the current login can switch to",
			description = "Every profile sharing the caller's phone (e.g. siblings), with current=true on the caller's "
					+ "own. A student login only lists students, never a staff profile on the same phone.")
	public ApiResponse<List<LoginProfile>> profiles() {
		return ApiResponse.success(otpService.switchableProfiles(AuthContext.current()));
	}

	@PostMapping("/api/v1/auth/profiles/switch")
	@Operation(summary = "Switch to another profile on the same phone",
			description = "Returns a new token for the chosen profile, with no new OTP. Replace the stored token with it.")
	public ApiResponse<LoginResponse> switchProfile(@Valid @RequestBody SwitchProfileRequest request) {
		return ApiResponse.success(otpService.switchProfile(
				AuthContext.current(), request.getOwnerType(), request.getOwnerId()), "Switched profile");
	}

}
