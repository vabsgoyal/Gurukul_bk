package com.gurukul.idcards.service;

import com.gurukul.chat.config.AttachmentProperties;
import com.gurukul.idcards.dto.IdCardDtos.PresignPhotoRequest;
import com.gurukul.idcards.dto.IdCardDtos.PresignPhotoResponse;
import com.gurukul.idcards.entity.IdCardOwnerType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Profile-photo storage for ID cards - the same presigned-upload pattern as SchoolLogoService, and
 * the same bucket and S3 beans (app.chat.attachments.*), under
 * {@code profile-photos/{schoolId}/{student|employee}/{ownerId}/}. No new bucket or env var.
 *
 * <p>Access control is the caller's job (IdCardService checks who may edit whose photo first).
 * With no bucket configured, uploads fail with a clear 400 and reads report "no photo", so the card
 * falls back to a placeholder silhouette.
 */
@Service
@RequiredArgsConstructor
public class ProfilePhotoService {

	private static final Logger log = LoggerFactory.getLogger(ProfilePhotoService.class);

	/** PNG and JPEG only - what the PDF renderer can embed. */
	static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg");
	static final long MAX_PHOTO_BYTES = 3L * 1024 * 1024;
	private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(5);

	private final AttachmentProperties properties;
	private final S3Presigner s3Presigner;
	private final S3Client s3Client;

	public record PhotoImage(byte[] bytes, String contentType) {
	}

	public PresignPhotoResponse presignUpload(UUID schoolId, IdCardOwnerType type, UUID ownerId, PresignPhotoRequest request) {
		requireConfigured();
		if (!ALLOWED_CONTENT_TYPES.contains(request.getContentType())) {
			throw new IllegalArgumentException("Photo must be a PNG or JPEG image");
		}
		if (request.getFileSizeBytes() > MAX_PHOTO_BYTES) {
			throw new IllegalArgumentException("Photo is too large - max 3 MB");
		}
		String extension = request.getContentType().equals("image/png") ? "png" : "jpg";
		String objectKey = "%s%s.%s".formatted(prefixFor(schoolId, type, ownerId), UUID.randomUUID(), extension);
		PutObjectRequest putRequest = PutObjectRequest.builder()
				.bucket(properties.bucket())
				.key(objectKey)
				.contentType(request.getContentType())
				.contentLength(request.getFileSizeBytes())
				.build();
		String url = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
						.signatureDuration(Duration.ofSeconds(properties.uploadExpirySeconds()))
						.putObjectRequest(putRequest)
						.build())
				.url().toString();
		return new PresignPhotoResponse(url, objectKey, Instant.now().plusSeconds(properties.uploadExpirySeconds()));
	}

	/** Checks an object key was presigned for exactly this person and the upload really exists. */
	public void requireUploaded(UUID schoolId, IdCardOwnerType type, UUID ownerId, String objectKey) {
		requireConfigured();
		if (!objectKey.startsWith(prefixFor(schoolId, type, ownerId)) || objectKey.contains("..")) {
			throw new IllegalArgumentException("That photo upload doesn't belong to this profile");
		}
		try {
			HeadObjectResponse head = s3Client.headObject(HeadObjectRequest.builder()
					.bucket(properties.bucket()).key(objectKey).build());
			if (head.contentLength() != null && head.contentLength() > MAX_PHOTO_BYTES) {
				throw new IllegalArgumentException("Photo is too large - max 3 MB");
			}
		} catch (NoSuchKeyException e) {
			throw new IllegalArgumentException("Photo upload not found - upload the file before confirming it");
		}
	}

	/** A fresh presigned GET url, or null if there's no photo or storage isn't usable right now. */
	public String photoUrl(String objectKey) {
		if (objectKey == null || !properties.isConfigured()) {
			return null;
		}
		try {
			return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
							.signatureDuration(Duration.ofSeconds(properties.downloadExpirySeconds()))
							.getObjectRequest(GetObjectRequest.builder()
									.bucket(properties.bucket()).key(objectKey).build())
							.build())
					.url().toString();
		} catch (RuntimeException e) {
			log.warn("Could not presign profile photo {}: {}", objectKey, e.toString());
			return null;
		}
	}

	/** The photo's bytes for embedding in a PDF, or empty (never an exception) if unavailable. */
	public Optional<PhotoImage> fetch(String objectKey) {
		if (objectKey == null || !properties.isConfigured()) {
			return Optional.empty();
		}
		try {
			ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(GetObjectRequest.builder()
					.bucket(properties.bucket())
					.key(objectKey)
					.overrideConfiguration(c -> c.apiCallTimeout(FETCH_TIMEOUT))
					.build());
			byte[] bytes = object.asByteArray();
			String contentType = object.response().contentType();
			if (!ALLOWED_CONTENT_TYPES.contains(contentType) || bytes.length > MAX_PHOTO_BYTES) {
				return Optional.empty();
			}
			return Optional.of(new PhotoImage(bytes, contentType));
		} catch (RuntimeException e) {
			log.warn("Could not fetch profile photo {}, using the placeholder: {}", objectKey, e.toString());
			return Optional.empty();
		}
	}

	static String prefixFor(UUID schoolId, IdCardOwnerType type, UUID ownerId) {
		return "profile-photos/%s/%s/%s/".formatted(schoolId, type.pathSegment(), ownerId);
	}

	private void requireConfigured() {
		if (!properties.isConfigured()) {
			throw new IllegalStateException("Profile photo upload is not configured on this server");
		}
	}

}
