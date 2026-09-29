package com.gurukul.schools.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

public class SchoolLogoDtos {

	@Getter @Setter
	@Schema(description = "Ask for a presigned upload URL for a new school logo (PNG or JPEG, max 2 MB)")
	public static class PresignLogoRequest {
		@NotBlank
		@Schema(example = "image/png")
		private String contentType;
		@NotNull @Positive
		private Long fileSizeBytes;
	}

	@Getter @AllArgsConstructor
	@Schema(description = "uploadUrl is a presigned PUT - upload the raw image bytes there with the same "
			+ "Content-Type, then confirm with PUT /api/v1/schools/{id}/logo {objectKey}")
	public static class PresignLogoResponse {
		private String uploadUrl;
		private String objectKey;
		private Instant expiresAt;
	}

	@Getter @Setter
	public static class SetLogoRequest {
		@NotBlank
		@Schema(description = "The objectKey returned by the presign call")
		private String objectKey;
	}

}
