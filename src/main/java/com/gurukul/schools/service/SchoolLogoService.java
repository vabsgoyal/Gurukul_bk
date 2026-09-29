package com.gurukul.schools.service;

import com.gurukul.chat.config.AttachmentProperties;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.schools.dto.SchoolLogoDtos.PresignLogoRequest;
import com.gurukul.schools.dto.SchoolLogoDtos.PresignLogoResponse;
import com.gurukul.schools.entity.School;
import com.gurukul.schools.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
 * School logo storage, following the chat AttachmentService pattern: the app uploads straight to S3
 * with a presigned PUT, then confirms the object key; only the key is stored on School, and a GET
 * url is presigned fresh on every read. Reuses the chat-attachments bucket and S3 beans
 * (app.chat.attachments.*), under a separate school-logos/{schoolId}/ prefix, so no new bucket or
 * env var is needed.
 *
 * <p>Everything here degrades gracefully when the bucket isn't configured (e.g. local dev): the
 * upload calls fail with a clear 400, while {@link #logoUrl} and {@link #fetchLogo} simply report
 * "no logo" so school reads and PDFs keep working with the Gurukul placeholder.
 */
@Service
@RequiredArgsConstructor
public class SchoolLogoService {

	private static final Logger log = LoggerFactory.getLogger(SchoolLogoService.class);

	/** PNG and JPEG only - the two formats OpenHTMLtoPDF can embed. */
	static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg");
	static final long MAX_LOGO_BYTES = 2L * 1024 * 1024;
	private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(5);

	private final AttachmentProperties properties;
	private final S3Presigner s3Presigner;
	private final S3Client s3Client;
	private final SchoolRepository schoolRepository;

	public record LogoImage(byte[] bytes, String contentType) {
	}

	public PresignLogoResponse presignUpload(UUID schoolId, PresignLogoRequest request) {
		requireConfigured();
		if (!ALLOWED_CONTENT_TYPES.contains(request.getContentType())) {
			throw new IllegalArgumentException("Logo must be a PNG or JPEG image");
		}
		if (request.getFileSizeBytes() > MAX_LOGO_BYTES) {
			throw new IllegalArgumentException("Logo is too large - max 2 MB");
		}
		String extension = request.getContentType().equals("image/png") ? "png" : "jpg";
		String objectKey = "%s%s.%s".formatted(prefixFor(schoolId), UUID.randomUUID(), extension);

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
		return new PresignLogoResponse(url, objectKey, Instant.now().plusSeconds(properties.uploadExpirySeconds()));
	}

	/** Points the school at an uploaded logo. The key must be one presigned for this school. */
	@Transactional
	public void setLogo(UUID schoolId, String objectKey) {
		requireConfigured();
		if (!objectKey.startsWith(prefixFor(schoolId)) || objectKey.contains("..")) {
			throw new IllegalArgumentException("That logo upload doesn't belong to this school");
		}
		try {
			HeadObjectResponse head = s3Client.headObject(HeadObjectRequest.builder()
					.bucket(properties.bucket()).key(objectKey).build());
			if (head.contentLength() != null && head.contentLength() > MAX_LOGO_BYTES) {
				throw new IllegalArgumentException("Logo is too large - max 2 MB");
			}
		} catch (NoSuchKeyException e) {
			throw new IllegalArgumentException("Logo upload not found - upload the file before confirming it");
		}
		School school = findSchool(schoolId);
		school.setLogoObjectKey(objectKey);
		schoolRepository.save(school);
	}

	@Transactional
	public void removeLogo(UUID schoolId) {
		School school = findSchool(schoolId);
		school.setLogoObjectKey(null);
		schoolRepository.save(school);
	}

	/** A fresh presigned GET url, or null if there's no logo or storage isn't usable right now. */
	public String logoUrl(School school) {
		if (school.getLogoObjectKey() == null || !properties.isConfigured()) {
			return null;
		}
		try {
			return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
							.signatureDuration(Duration.ofSeconds(properties.downloadExpirySeconds()))
							.getObjectRequest(GetObjectRequest.builder()
									.bucket(properties.bucket()).key(school.getLogoObjectKey()).build())
							.build())
					.url().toString();
		} catch (RuntimeException e) {
			log.warn("Could not presign logo for school {}: {}", school.getId(), e.toString());
			return null;
		}
	}

	/** The logo's bytes for embedding in a PDF, or empty (never an exception) if unavailable. */
	public Optional<LogoImage> fetchLogo(UUID schoolId) {
		if (!properties.isConfigured()) {
			return Optional.empty();
		}
		try {
			School school = schoolRepository.findById(schoolId).orElse(null);
			if (school == null || school.getLogoObjectKey() == null) {
				return Optional.empty();
			}
			ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(GetObjectRequest.builder()
					.bucket(properties.bucket())
					.key(school.getLogoObjectKey())
					.overrideConfiguration(c -> c.apiCallTimeout(FETCH_TIMEOUT))
					.build());
			byte[] bytes = object.asByteArray();
			String contentType = object.response().contentType();
			if (!ALLOWED_CONTENT_TYPES.contains(contentType) || bytes.length > MAX_LOGO_BYTES) {
				return Optional.empty();
			}
			return Optional.of(new LogoImage(bytes, contentType));
		} catch (RuntimeException e) {
			log.warn("Could not fetch logo for school {}, using the placeholder: {}", schoolId, e.toString());
			return Optional.empty();
		}
	}

	static String prefixFor(UUID schoolId) {
		return "school-logos/" + schoolId + "/";
	}

	private void requireConfigured() {
		if (!properties.isConfigured()) {
			throw new IllegalStateException("School logo upload is not configured on this server");
		}
	}

	private School findSchool(UUID id) {
		return schoolRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("School not found"));
	}

}
