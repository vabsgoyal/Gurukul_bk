package com.gurukul.admissions.dto;

import com.gurukul.admissions.entity.AdmissionApplication;
import com.gurukul.admissions.entity.AdmissionDocument;
import com.gurukul.admissions.entity.AdmissionDocumentType;
import com.gurukul.admissions.entity.AdmissionStage;
import com.gurukul.students.entity.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class AdmissionDtos {

	private AdmissionDtos() {
	}

	@Getter @Setter
	@Schema(description = "Admission application entered by an admin. The section is assigned later, at enrolment.")
	public static class AdmissionRequest {
		@NotBlank @Size(max = 255) private String studentName;
		@NotNull @Past private LocalDate dob;
		@NotNull private Gender gender;
		@NotBlank @Size(max = 500) private String address;
		@Size(max = 255) private String previousSchoolName;
		@NotBlank @Size(max = 255) private String parentName;
		@NotBlank @Size(max = 50) private String parentContact;
		@Size(max = 255) private String parentEmail;
		@NotBlank @Size(max = 100)
		@Schema(description = "Class applied for, as returned by GET /api/v1/class-sections/classes", example = "Grade 5")
		private String appliedClassName;
		@Size(max = 2000) private String notes;
	}

	@Getter @Setter
	public static class StageChangeRequest {
		@NotNull
		@Schema(description = "Target stage. ENROLLED is not allowed here - use POST /{id}/convert.")
		private AdmissionStage stage;
	}

	@Getter @Setter
	public static class PresignDocumentRequest {
		@NotNull private AdmissionDocumentType documentType;
		@NotBlank private String fileName;
		@NotBlank private String contentType;
		@NotNull @Positive private Long fileSizeBytes;
	}

	@Getter @AllArgsConstructor
	@Schema(description = "uploadUrl is a presigned PUT - upload the raw bytes there, then POST /{id}/documents with objectKey")
	public static class PresignDocumentResponse {
		private String uploadUrl;
		private String objectKey;
		private Instant expiresAt;
	}

	@Getter @Setter
	public static class RegisterDocumentRequest {
		@NotNull private AdmissionDocumentType documentType;
		@NotBlank private String objectKey;
		@NotBlank @Size(max = 255) private String fileName;
		@NotBlank private String contentType;
		@NotNull @Positive private Long fileSizeBytes;
	}

	@Getter @Setter
	public static class ConvertRequest {
		@NotNull
		@Schema(description = "Section to enrol into - must be a section of the class applied for")
		private UUID classSectionId;
		@PastOrPresent
		@Schema(description = "Admission date; defaults to today")
		private LocalDate admissionDate;
		@Schema(description = "Also generate a 72h parent/student self-registration invite code")
		private boolean sendParentInvite;
		@Schema(description = "Enrol even though a student with the same name, DOB and parent phone already exists")
		private boolean allowDuplicate;
	}

	@Getter @AllArgsConstructor
	public static class DocumentResponse {
		private UUID id;
		private AdmissionDocumentType documentType;
		private String fileName;
		private String contentType;
		private long fileSizeBytes;
		@Schema(description = "Time-limited presigned GET link; null when document storage isn't configured")
		private String downloadUrl;
		private Instant createdAt;

		public static DocumentResponse from(AdmissionDocument d, String downloadUrl) {
			return new DocumentResponse(d.getId(), d.getDocumentType(), d.getFileName(), d.getContentType(),
					d.getFileSizeBytes(), downloadUrl, d.getCreatedAt());
		}
	}

	@Getter @AllArgsConstructor
	@Schema(description = "An existing student who looks like the same child (same name, DOB and parent phone)")
	public static class DuplicateStudent {
		private UUID id;
		private String name;
		private String rollNumber;
		private String classSectionLabel;
	}

	@Getter @AllArgsConstructor
	public static class AdmissionResponse {
		private UUID id;
		private AdmissionStage stage;
		private String studentName;
		private LocalDate dob;
		private Gender gender;
		private String address;
		private String previousSchoolName;
		private String parentName;
		private String parentContact;
		private String parentEmail;
		private String appliedClassName;
		private UUID assignedClassSectionId;
		private String notes;
		private UUID studentId;
		private Instant decidedAt;
		private Instant enrolledAt;
		private Instant createdAt;
		private Instant updatedAt;
		@Schema(description = "Detail only (null in lists)")
		private List<DocumentResponse> documents;
		@Schema(description = "Detail only (null in lists): existing students matching name + DOB + parent phone")
		private List<DuplicateStudent> possibleDuplicates;
		@Schema(description = "Detail only (null in lists): whether document upload is configured on this server")
		private Boolean documentUploadsEnabled;

		public static AdmissionResponse summary(AdmissionApplication a) {
			return detail(a, null, null, null);
		}

		public static AdmissionResponse detail(AdmissionApplication a, List<DocumentResponse> documents,
				List<DuplicateStudent> possibleDuplicates, Boolean documentUploadsEnabled) {
			return new AdmissionResponse(a.getId(), a.getStage(), a.getStudentName(), a.getDob(), a.getGender(),
					a.getAddress(), a.getPreviousSchoolName(), a.getParentName(), a.getParentContact(),
					a.getParentEmail(), a.getAppliedClassName(), a.getAssignedClassSectionId(), a.getNotes(),
					a.getStudentId(), a.getDecidedAt(), a.getEnrolledAt(), a.getCreatedAt(), a.getUpdatedAt(),
					documents, possibleDuplicates, documentUploadsEnabled);
		}
	}

	@Getter @AllArgsConstructor
	public static class ConvertResponse {
		private AdmissionResponse application;
		private UUID studentId;
		private String studentName;
		@Schema(description = "Server-assigned: alphabetical rank among the section's active students")
		private String rollNumber;
		private String registrationNumber;
		private String classSectionLabel;
		@Schema(description = "True when this application was already enrolled - no new student was created")
		private boolean alreadyEnrolled;
		@Schema(description = "Present when sendParentInvite was requested")
		private String inviteCode;
		private Instant inviteExpiresAt;
	}

}
