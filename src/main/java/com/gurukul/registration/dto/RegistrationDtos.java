package com.gurukul.registration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

public class RegistrationDtos {

	@Getter @Setter
	@Schema(description = "Self-registration for a student - claims the existing Student record created at admission "
			+ "by registrationNumber; auto-activates immediately since admin already vetted the record")
	public static class StudentRegistrationRequest {
		@NotBlank
		@Schema(description = "System-generated at admission time, e.g. 2026000001 - not the roll number")
		private String registrationNumber;
		@NotBlank @Size(min = 3, max = 50) private String username;
		@NotBlank @Size(min = 8) private String password;
	}

	@Getter @Setter
	@Schema(description = "Self-registration for a teacher - claims the specific Employee record the invite code was "
			+ "issued for; auto-activates immediately since admin already vetted the record")
	public static class TeacherRegistrationRequest {
		@NotBlank private String inviteCode;
		@NotBlank @Size(min = 3, max = 50) private String username;
		@NotBlank @Size(min = 8) private String password;
	}

	@Getter @Setter
	@Schema(description = "Self-registration for a parent - links to an existing student by registrationNumber, "
			+ "verified by matching parentContact against the value already on the student record; goes to admin approval")
	public static class ParentRegistrationRequest {
		@NotBlank
		@Schema(description = "The child's registrationNumber, e.g. 2026000001 - not the roll number, which isn't "
				+ "unique per school and can change as classmates join/leave")
		private String studentRegistrationNumber;
		@NotBlank
		@Schema(description = "Must match the parentContact already on file for this student")
		private String parentContact;
		@NotBlank @Size(min = 3, max = 50) private String username;
		@NotBlank @Size(min = 8) private String password;
	}

	@Getter @Setter
	@Schema(description = "Link an additional child to an already-approved parent account (e.g. a sibling)")
	public static class LinkChildRequest {
		@NotBlank
		@Schema(description = "The child's registrationNumber, e.g. 2026000001")
		private String studentRegistrationNumber;
	}

	@Getter @Setter
	@Schema(description = "Self-registration for a student via Google - idToken replaces username/password; "
			+ "the verified Google email becomes the username; claims the existing Student record by registrationNumber")
	public static class StudentGoogleRegistrationRequest {
		@NotBlank private String idToken;
		@NotBlank
		@Schema(description = "System-generated at admission time, e.g. 2026000001 - not the roll number")
		private String registrationNumber;
	}

	@Getter @Setter
	@Schema(description = "Self-registration for a teacher via Google - still requires an admin-issued invite code, "
			+ "claims the specific Employee record it was issued for")
	public static class TeacherGoogleRegistrationRequest {
		@NotBlank private String idToken;
		@NotBlank private String inviteCode;
	}

	@Getter @Setter
	@Schema(description = "Self-registration for a parent via Google, linking to an existing student by "
			+ "registrationNumber, verified by matching parentContact against the value already on the student record")
	public static class ParentGoogleRegistrationRequest {
		@NotBlank private String idToken;
		@NotBlank
		@Schema(description = "The child's registrationNumber, e.g. 2026000001")
		private String studentRegistrationNumber;
		@NotBlank
		@Schema(description = "Must match the parentContact already on file for this student")
		private String parentContact;
	}

	@Getter @AllArgsConstructor
	@Schema(description = "Result of a successful self-registration submission - no login yet, pending approval")
	public static class RegistrationSubmittedResponse {
		private UUID entityId;
		private String message;
	}

	@Getter @AllArgsConstructor
	@Schema(description = "One pending or decided registration, for the admin approval inbox")
	public static class PendingRegistrationResponse {
		private UUID entityId;
		private String entityType;
		private String displayName;
		private String submittedBy;
		private Instant submittedAt;
	}

	@Getter @Setter
	@Schema(description = "Optional comment when approving/rejecting a registration")
	public static class DecisionRequest {
		private String comment;
	}

	@Getter @AllArgsConstructor
	@Schema(description = "A freshly generated teacher invite - share the code/link with the prospective teacher")
	public static class TeacherInviteResponse {
		private String code;
		private Instant expiresAt;
	}

}
