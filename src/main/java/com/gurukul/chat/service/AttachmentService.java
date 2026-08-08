package com.gurukul.chat.service;

import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.chat.config.AttachmentProperties;
import com.gurukul.chat.dto.ChatDtos.PresignAttachmentRequest;
import com.gurukul.chat.dto.ChatDtos.PresignAttachmentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Chat attachments (images/PDFs) go straight from the client to S3 via a presigned PUT - file
 * bytes never touch this backend. content-type/size are validated here, before any presigned URL
 * is handed out, so a rejected upload never reaches the bucket in the first place. objectKey (not
 * a raw URL) is what gets persisted on Message - a GET url is freshly presigned on every read (see
 * presignDownload), so a message from months ago never shows an "expired link".
 */
@Service
@RequiredArgsConstructor
public class AttachmentService {

	private final AttachmentProperties properties;
	private final S3Presigner s3Presigner;

	public boolean isConfigured() {
		return properties.isConfigured();
	}

	public PresignAttachmentResponse presignUpload(AuthPrincipal principal, UUID conversationId, PresignAttachmentRequest request) {
		requireConfigured();
		if (!properties.allowedContentTypeSet().contains(request.getContentType())) {
			throw new IllegalArgumentException("Unsupported file type: " + request.getContentType());
		}
		if (request.getFileSizeBytes() > properties.maxFileSizeBytes()) {
			throw new IllegalArgumentException(
					"File is too large - max " + (properties.maxFileSizeBytes() / (1024 * 1024)) + " MB");
		}

		String objectKey = "chat-attachments/%s/%s/%s-%s".formatted(
				principal.getSchoolId(), conversationId, UUID.randomUUID(), sanitizeFileName(request.getFileName()));

		PutObjectRequest putRequest = PutObjectRequest.builder()
				.bucket(properties.bucket())
				.key(objectKey)
				.contentType(request.getContentType())
				.contentLength(request.getFileSizeBytes())
				.build();
		PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
				.signatureDuration(Duration.ofSeconds(properties.uploadExpirySeconds()))
				.putObjectRequest(putRequest)
				.build());

		return new PresignAttachmentResponse(
				presigned.url().toString(), objectKey, Instant.now().plusSeconds(properties.uploadExpirySeconds()));
	}

	/** Returns null if objectKey is null - callers pass this straight through for attachment-less messages. */
	public String presignDownload(String objectKey) {
		if (objectKey == null) {
			return null;
		}
		requireConfigured();
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

	private void requireConfigured() {
		if (!properties.isConfigured()) {
			throw new IllegalStateException("Chat attachments are not configured on this server");
		}
	}

	/** Strips path separators and anything not alphanumeric/dot/dash/underscore, so the object key is never surprising. */
	private String sanitizeFileName(String fileName) {
		String base = fileName.contains("/") ? fileName.substring(fileName.lastIndexOf('/') + 1) : fileName;
		return base.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

}
