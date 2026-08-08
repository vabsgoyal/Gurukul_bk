package com.gurukul.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3FileStorageService implements FileStorageService {

	private static final long MAX_FILE_SIZE_BYTES = 25L * 1024 * 1024;
	private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
			"application/pdf",
			"application/msword",
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
			"application/vnd.ms-powerpoint",
			"application/vnd.openxmlformats-officedocument.presentationml.presentation",
			"image/png",
			"image/jpeg",
			"image/webp",
			"video/mp4",
			"video/quicktime");

	private final S3Client s3Client;
	private final StorageProperties properties;

	@Override
	public StoredFile upload(MultipartFile file, String keyPrefix) {
		if (!properties.isConfigured()) {
			throw new IllegalStateException("File uploads aren't configured yet - please contact your school admin.");
		}
		if (file.isEmpty()) {
			throw new IllegalArgumentException("The uploaded file is empty.");
		}
		if (file.getSize() > MAX_FILE_SIZE_BYTES) {
			throw new IllegalArgumentException("The uploaded file exceeds the 25MB limit.");
		}
		String contentType = file.getContentType();
		if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
			throw new IllegalArgumentException("Unsupported file type: " + contentType);
		}

		String originalFilename = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "file";
		String key = keyPrefix + "/" + UUID.randomUUID() + "-" + sanitize(originalFilename);

		try {
			s3Client.putObject(
					PutObjectRequest.builder()
							.bucket(properties.s3Bucket())
							.key(key)
							.contentType(contentType)
							.build(),
					RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
		} catch (IOException ex) {
			throw new UncheckedIOException("Could not read the uploaded file.", ex);
		}

		String url = "https://" + properties.s3Bucket() + ".s3." + properties.s3Region() + ".amazonaws.com/" + key;
		return new StoredFile(url, key, contentType, file.getSize(), originalFilename);
	}

	private String sanitize(String filename) {
		return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

}
