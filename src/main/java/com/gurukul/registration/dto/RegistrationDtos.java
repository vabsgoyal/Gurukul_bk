package com.gurukul.registration.dto;

import com.gurukul.students.entity.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class RegistrationDtos {

	@Getter @Setter
	@Schema(description = "Self-registration for a student - goes to admin approval before login works")
	public static class StudentRegistrationRequest {
		@NotBlank private String rollNumber;
		@NotBlank private String name;
		@NotNull @Past private LocalDate dob;
		@NotNull private Gender gender;
		@NotBlank private String address;
		@NotBlank private String parentName;
		@NotBlank private String parentContact;
		@NotNull private UUID classSectionId;
		@NotNull @PastOrPresent private LocalDate admissionDate;
		@NotBlank @Size(min = 3, max = 50) private String username;
		@NotBlank @Size(min = 8) private String password;
	}

	@Getter @Setter
	@Schema(description = "Self-registration for a teacher - requires an admin-issued invite code")
	public static class TeacherRegistrationRequest {
		@NotBlank private String inviteCode;
		@NotBlank private String name;
		@NotBlank private String designation;
		@NotNull private LocalDate joinDate;
		private String contactPhone;
		@Email private String contactEmail;
		@NotBlank @Size(min = 3, max = 50) private String username;
		@NotBlank @Size(min = 8) private String password;
	}

	@Getter @Setter
	@Schema(description = "Self-registration for a parent - links to an existing student by roll number")
	public static class ParentRegistrationRequest {
		@NotBlank private String name;
		@Email private String email;
		private String phone;
		@NotBlank
		@Schema(description = "Roll number of the child this parent is linking to")
		private String studentRollNumber;
		@NotBlank @Size(min = 3, max = 50) private String username;
		@NotBlank @Size(min = 8) private String password;
	}

	@Getter @Setter
	@Schema(description = "Link an additional child to an already-approved parent account (e.g. a sibling)")
	public static class LinkChildRequest {
		@NotBlank private String studentRollNumber;
	}

	@Getter @Setter
	@Schema(description = "Self-registration for a student via Google - idToken replaces username/password; "
			+ "the verified Google email becomes the username")
	public static class StudentGoogleRegistrationRequest {
		@NotBlank private String idToken;
		@NotBlank private String rollNumber;
		@NotBlank private String name;
		@NotNull @Past private LocalDate dob;
		@NotNull private Gender gender;
		@NotBlank private String address;
		@NotBlank private String parentName;
		@NotBlank private String parentContact;
		@NotNull private UUID classSectionId;
		@NotNull @PastOrPresent private LocalDate admissionDate;
	}

	@Getter @Setter
	@Schema(description = "Self-registration for a teacher via Google - still requires an admin-issued invite code")
	public static class TeacherGoogleRegistrationRequest {
		@NotBlank private String idToken;
		@NotBlank private String inviteCode;
		@NotBlank private String name;
		@NotBlank private String designation;
		@NotNull private LocalDate joinDate;
		private String contactPhone;
	}

	@Getter @Setter
	@Schema(description = "Self-registration for a parent via Google, linking to an existing student by roll number")
	public static class ParentGoogleRegistrationRequest {
		@NotBlank private String idToken;
		@NotBlank private String name;
		private String phone;
		@NotBlank
		@Schema(description = "Roll number of the child this parent is linking to")
		private String studentRollNumber;
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
