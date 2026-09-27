package com.gurukul.auth.dto;

import com.gurukul.auth.dto.AuthDtos.LoginResponse;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

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

	@Getter @AllArgsConstructor
	@Schema(description = "OTP verify result. If the phone belongs to one profile, the login fields are set exactly as "
			+ "before and profileSelectionRequired is false. If it belongs to several (e.g. siblings sharing a "
			+ "parent's number), token is null, profileSelectionRequired is true, and the client shows `profiles` "
			+ "and calls POST /api/v1/auth/otp/select-profile with the selectionToken and the chosen profile.")
	public static class OtpVerifyResponse {
		private String token;
		private String tokenType;
		private OwnerType ownerType;
		private UUID ownerId;
		private Role role;
		private UUID schoolId;
		private String username;
		private boolean profileSelectionRequired;
		@Schema(description = "Valid for 10 minutes; only accepted by select-profile, never as a Bearer token")
		private String selectionToken;
		private List<LoginProfile> profiles;

		public static OtpVerifyResponse loggedIn(LoginResponse login, List<LoginProfile> profiles) {
			return new OtpVerifyResponse(login.getToken(), login.getTokenType(), login.getOwnerType(), login.getOwnerId(),
					login.getRole(), login.getSchoolId(), login.getUsername(), false, null, profiles);
		}

		public static OtpVerifyResponse selectionRequired(UUID schoolId, String selectionToken, List<LoginProfile> profiles) {
			return new OtpVerifyResponse(null, null, null, null, null, schoolId, null, true, selectionToken, profiles);
		}
	}

	@Getter @AllArgsConstructor
	@Schema(description = "One profile a phone number can log in as - a staff member or a student (child)")
	public static class LoginProfile {
		private OwnerType ownerType;
		private UUID ownerId;
		private String name;
		@Schema(description = "Role the login will have: TEACHER/ADMIN for staff, STUDENT for a child")
		private Role role;
		@Schema(description = "Students only, e.g. \"8\"")
		private String className;
		@Schema(description = "Students only, e.g. \"A\"")
		private String section;
		@Schema(description = "Students only")
		private String rollNumber;
		@Schema(description = "Students only: ACTIVE, ALUMNI or WITHDRAWN")
		private String status;
		@Schema(description = "True for the profile the current token belongs to (GET /auth/profiles only)")
		private boolean current;
	}

	@Getter @Setter
	@Schema(description = "Pick the profile to log in as, after an OTP verify that returned profileSelectionRequired")
	public static class SelectProfileRequest {
		@NotBlank private String selectionToken;
		@NotNull private OwnerType ownerType;
		@NotNull private UUID ownerId;
	}

	@Getter @Setter
	@Schema(description = "Switch the logged-in session to another profile sharing the same phone")
	public static class SwitchProfileRequest {
		@NotNull private OwnerType ownerType;
		@NotNull private UUID ownerId;
	}

}
