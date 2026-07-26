package com.gurukul.auth.controller;

import com.gurukul.auth.dto.AuthDtos.LoginResponse;
import com.gurukul.auth.dto.OtpDtos.OtpRequest;
import com.gurukul.auth.dto.OtpDtos.OtpVerifyRequest;
import com.gurukul.auth.service.OtpService;
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
@Tag(name = "OTP Login", description = "Phone + OTP login (dummy OTP for now). Requires X-School-Id header.")
public class OtpController {

	private final OtpService otpService;

	@PostMapping("/api/v1/auth/otp/request")
	@Operation(summary = "Request an OTP",
			description = "Dummy for now - no real SMS is sent, the code is always 1234. Errors if the phone isn't on file.")
	public ApiResponse<Void> requestOtp(@Valid @RequestBody OtpRequest request) {
		otpService.requestOtp(request.getPhone());
		return ApiResponse.success(null, "OTP sent");
	}

	@PostMapping("/api/v1/auth/otp/verify")
	@Operation(summary = "Verify an OTP and log in",
			description = "Auto-creates a login (TEACHER for an Employee's phone, STUDENT for a Student's parentContact) "
					+ "the first time a phone number verifies successfully. Never auto-creates ADMIN.")
	public ApiResponse<LoginResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
		return ApiResponse.success(otpService.verifyOtp(request.getPhone(), request.getOtp()), "Login successful");
	}

}
