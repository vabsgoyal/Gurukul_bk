package com.gurukul.chat.service;

import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.chat.dto.ChatDtos.PresignAttachmentRequest;
import com.gurukul.chat.dto.ChatDtos.PresignAttachmentResponse;
import com.gurukul.common.storage.S3PresignHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Chat attachments (images/PDFs) go straight from the client to S3 via a presigned PUT - file
 * bytes never touch this backend. content-type/size are validated here, before any presigned URL
 * is handed out, so a rejected upload never reaches the bucket in the first place. objectKey (not
 * a raw URL) is what gets persisted on Message - a GET url is freshly presigned on every read (see
 * presignDownload), so a message from months ago never shows an "expired link". The presigning
 * itself lives in S3PresignHelper (shared with admission documents).
 */
@Service
@RequiredArgsConstructor
public class AttachmentService {

	private final S3PresignHelper presignHelper;

	public boolean isConfigured() {
		return presignHelper.isConfigured();
	}

	public PresignAttachmentResponse presignUpload(AuthPrincipal principal, UUID conversationId, PresignAttachmentRequest request) {
		requireConfigured();
		presignHelper.validateUpload(request.getContentType(), request.getFileSizeBytes());

		String objectKey = "chat-attachments/%s/%s/%s-%s".formatted(
				principal.getSchoolId(), conversationId, UUID.randomUUID(),
				S3PresignHelper.sanitizeFileName(request.getFileName()));

		S3PresignHelper.PresignedUpload presigned =
				presignHelper.presignUpload(objectKey, request.getContentType(), request.getFileSizeBytes());
		return new PresignAttachmentResponse(presigned.uploadUrl(), presigned.objectKey(), presigned.expiresAt());
	}

	/** Returns null if objectKey is null - callers pass this straight through for attachment-less messages. */
	public String presignDownload(String objectKey) {
		if (objectKey == null) {
			return null;
		}
		requireConfigured();
		return presignHelper.presignDownload(objectKey);
	}

	private void requireConfigured() {
		if (!presignHelper.isConfigured()) {
			throw new IllegalStateException("Chat attachments are not configured on this server");
		}
	}

}
