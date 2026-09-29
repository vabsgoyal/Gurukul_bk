package com.gurukul.idcards.dto;

import com.gurukul.idcards.entity.IdCardOwnerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class IdCardDtos {

	@Getter @Setter
	@Schema(description = "The ID-card details a person fills in on their profile. A blank/null field clears it.")
	public static class UpdateIdCardProfileRequest {
		@Schema(description = "One of A+, A-, B+, B-, AB+, AB-, O+, O-", example = "B+")
		private String bloodGroup;
		@Size(max = 120)
		@Schema(example = "Sunita Sharma")
		private String emergencyContactName;
		@Schema(description = "7-15 digits, optional leading +; spaces/dashes are ignored", example = "+91 98765 43210")
		private String emergencyPhone;
	}

	@Getter @Setter
	@Schema(description = "Ask for a presigned upload URL for a profile photo (PNG or JPEG, max 3 MB)")
	public static class PresignPhotoRequest {
		@NotBlank
		@Schema(example = "image/jpeg")
		private String contentType;
		@NotNull @Positive
		private Long fileSizeBytes;
	}

	@Getter @AllArgsConstructor
	@Schema(description = "uploadUrl is a presigned PUT - upload the raw image bytes there with the same "
			+ "Content-Type, then confirm with PUT .../photo {objectKey}")
	public static class PresignPhotoResponse {
		private String uploadUrl;
		private String objectKey;
		private Instant expiresAt;
	}

	@Getter @Setter
	public static class SetPhotoRequest {
		@NotBlank
		@Schema(description = "The objectKey returned by the presign call")
		private String objectKey;
	}

	@Getter @Builder
	@Schema(description = "Everything an ID card shows, plus what's still missing from the profile")
	public static class IdCardResponse {
		private IdCardOwnerType ownerType;
		private UUID ownerId;
		private String name;
		@Schema(description = "Students only, e.g. \"Grade 5 - A\"")
		private String classSectionLabel;
		@Schema(description = "Students only")
		private String rollNumber;
		@Schema(description = "Students only - the class-section's academic year, shown as the card's validity")
		private String academicYear;
		@Schema(description = "Students only")
		private String parentName;
		@Schema(description = "Staff only")
		private String designation;
		private String bloodGroup;
		private String emergencyContactName;
		@Schema(description = "As entered on the profile (may be null)")
		private String emergencyPhone;
		@Schema(description = "The phone printed on the card: the emergency phone, else the parent contact "
				+ "(students) or own contact phone (staff)")
		private String cardPhone;
		@Schema(description = "Fresh presigned GET url, null when there's no photo or storage isn't configured")
		private String photoUrl;
		private boolean hasPhoto;
		@Schema(description = "What the profile still lacks: PHOTO, BLOOD_GROUP, EMERGENCY_CONTACT. "
				+ "The card downloads either way, with placeholders.")
		private List<String> missing;
		@Schema(description = "Whether the caller may edit these details (self, or a linked parent for a student)")
		private boolean canEdit;
		private String status;
		private String schoolName;
		private String schoolAddress;
		@Schema(description = "The signed identifier encoded in the QR code")
		private String qrCode;
		@Schema(description = "The QR code as a PNG data URI, ready for an <Image> source")
		private String qrImage;
	}

	@Getter @Builder
	@Schema(description = "Who a scanned ID-card QR belongs to - identification only")
	public static class IdCardVerifyResponse {
		private IdCardOwnerType ownerType;
		private UUID ownerId;
		private String name;
		private String classSectionLabel;
		private String rollNumber;
		private String designation;
		private String photoUrl;
		private String status;
		private boolean active;
	}

}
