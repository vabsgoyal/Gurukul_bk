package com.gurukul.common.storage;

import com.gurukul.chat.config.AttachmentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;

/**
 * Presigned S3 upload/download for any feature that stores private files in the app bucket (chat
 * attachments, admission documents). Extracted from AttachmentService so each feature only decides
 * its own object-key prefix and "not configured" message - bucket, expiries, allowed types and
 * size cap all come from the existing app.chat.attachments.* properties. File bytes never touch
 * this backend: the client PUTs straight to S3, and a GET link is signed fresh on every read so a
 * stored objectKey never turns into an expired URL.
 *
 * Callers must check isConfigured() first (and fail with their own message) - these methods assume
 * a bucket is set.
 */
@Component
@RequiredArgsConstructor
public class S3PresignHelper {

	private final AttachmentProperties properties;
	private final S3Presigner s3Presigner;

	public record PresignedUpload(String uploadUrl, String objectKey, Instant expiresAt) {
	}

	public boolean isConfigured() {
		return properties.isConfigured();
	}

	/** Rejects a disallowed content type or an oversized file before any URL is handed out. */
	public void validateUpload(String contentType, long fileSizeBytes) {
		if (!properties.allowedContentTypeSet().contains(contentType)) {
			throw new IllegalArgumentException("Unsupported file type: " + contentType);
		}
		if (fileSizeBytes > properties.maxFileSizeBytes()) {
			throw new IllegalArgumentException(
					"File is too large - max " + (properties.maxFileSizeBytes() / (1024 * 1024)) + " MB");
		}
	}

	public PresignedUpload presignUpload(String objectKey, String contentType, long fileSizeBytes) {
		PutObjectRequest putRequest = PutObjectRequest.builder()
				.bucket(properties.bucket())
				.key(objectKey)
				.contentType(contentType)
				.contentLength(fileSizeBytes)
				.build();
		PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
				.signatureDuration(Duration.ofSeconds(properties.uploadExpirySeconds()))
				.putObjectRequest(putRequest)
				.build());
		return new PresignedUpload(
				presigned.url().toString(), objectKey, Instant.now().plusSeconds(properties.uploadExpirySeconds()));
	}

	public String presignDownload(String objectKey) {
		GetObjectRequest getRequest = GetObjectRequest.builder()
				.bucket(properties.bucket())
				.key(objectKey)
				.build();
		PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
				.signatureDuration(Duration.ofSeconds(properties.downloadExpirySeconds()))
				.getObjectRequest(getRequest)
				.build());
		return presigned.url().toString();
	}

	/** Strips path separators and anything not alphanumeric/dot/dash/underscore, so the object key is never surprising. */
	public static String sanitizeFileName(String fileName) {
		String base = fileName.contains("/") ? fileName.substring(fileName.lastIndexOf('/') + 1) : fileName;
		return base.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

}
