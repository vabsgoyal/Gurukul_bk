package com.gurukul.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

public class OtpDtos {

	@Getter @Setter
	@Schema(description = "Request an OTP for a phone number already on file (Employee.contactPhone or Student.parentContact)")
	public static class OtpRequest {
		@NotBlank private String phone;
	}

	@Getter @Setter
	@Schema(description = "Verify an OTP and log in")
	public static class OtpVerifyRequest {
		@NotBlank private String phone;
		@NotBlank private String otp;
	}

}
