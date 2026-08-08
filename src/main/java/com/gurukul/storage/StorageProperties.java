package com.gurukul.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * s3Bucket has no default - a missing bucket must fail loudly the first time an upload is
 * attempted (S3FileStorageService checks isConfigured() before ever calling S3), not silently
 * misbehave. s3Region falls back to the same default used elsewhere for AWS_REGION.
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String s3Bucket, String s3Region) {

	public boolean isConfigured() {
		return s3Bucket != null && !s3Bucket.isBlank();
	}

}
