package com.gurukul.auth.dto;

import com.gurukul.auth.entity.Credential;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

public class AuthDtos {

	@Getter @Setter
	@Schema(description = "Login request")
	public static class LoginRequest {
		@NotBlank private String username;
		@NotBlank private String password;
	}

	@Getter @Setter
	@Schema(description = "Google Sign-In login/registration request - idToken is the credential "
			+ "from Google's own SDK on the client (One Tap / Google Identity Services / native "
			+ "Sign-In), never a password")
	public static class GoogleIdTokenRequest {
		@NotBlank private String idToken;
	}

	@Getter @AllArgsConstructor
	@Schema(description = "Login response - use the token as an Authorization: Bearer header on subsequent requests")
	public static class LoginResponse {
		private String token;
		private String tokenType;
		private OwnerType ownerType;
		private UUID ownerId;
		private Role role;
		private UUID schoolId;
		private String username;
		@Schema(description = "Exchange at POST /api/v1/auth/refresh for a new token pair before or after the access "
				+ "token expires. Single use - always store the new one. Keep it in secure storage.")
		private String refreshToken;
		private Instant accessTokenExpiresAt;
		@Schema(description = "Slides forward on every refresh; the user is only logged out after this long unused")
		private Instant refreshTokenExpiresAt;
	}

	@Getter @Setter
	@Schema(description = "Refresh token from the last login or refresh")
	public static class RefreshTokenRequest {
		@NotBlank private String refreshToken;
	}

	@Getter @Setter
	@Schema(description = "Provision a login credential for an employee or student")
	public static class CredentialRequest {
		@NotBlank private String username;
		@NotBlank private String password;
		@NotNull private Role role;
	}

	@Getter @AllArgsConstructor
	@Schema(description = "Created credential (never returns the password/hash)")
	public static class CredentialResponse {
		private UUID id;
		private OwnerType ownerType;
		private UUID ownerId;
		private String username;
		private Role role;
		private Instant createdAt;

		public static CredentialResponse from(Credential credential) {
			return new CredentialResponse(
					credential.getId(),
					credential.getOwnerType(),
					credential.getOwnerId(),
					credential.getUsername(),
					credential.getRole(),
					credential.getCreatedAt()
			);
		}
	}

}
