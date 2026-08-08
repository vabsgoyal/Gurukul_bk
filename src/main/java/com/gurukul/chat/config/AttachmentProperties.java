package com.gurukul.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Set;

/**
 * bucket has no real default - a blank bucket must fail loudly (AttachmentService checks
 * isConfigured() before ever presigning), never silently "work". allowedContentTypes is a
 * comma-separated list in properties, parsed into a Set here for O(1) lookup.
 */
@ConfigurationProperties(prefix = "app.chat.attachments")
public record AttachmentProperties(
		String bucket,
		String region,
		long maxFileSizeBytes,
		String allowedContentTypes,
		int uploadExpirySeconds,
		int downloadExpirySeconds) {

	public boolean isConfigured() {
		return bucket != null && !bucket.isBlank();
	}

	public Set<String> allowedContentTypeSet() {
		return Set.copyOf(List.of(allowedContentTypes.split(",")));
	}
}
