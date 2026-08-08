package com.gurukul.chat.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Built even when the bucket is unconfigured, same fail-open-at-call-time pattern as
 * AnthropicClientConfig - AttachmentService checks properties.isConfigured() itself before ever
 * using these, so app startup never fails on a missing bucket. Credentials resolved via the
 * default AWS provider chain (the EC2 instance role in prod, ~/.aws/credentials locally) - no
 * explicit access key ever appears in this codebase.
 */
@Configuration
@EnableConfigurationProperties(AttachmentProperties.class)
public class AttachmentS3Config {

	@Bean
	public S3Client attachmentS3Client(AttachmentProperties properties) {
		return S3Client.builder()
				.region(Region.of(properties.region()))
				.build();
	}

	@Bean
	public S3Presigner attachmentS3Presigner(AttachmentProperties properties) {
		return S3Presigner.builder()
				.region(Region.of(properties.region()))
				.build();
	}

}
