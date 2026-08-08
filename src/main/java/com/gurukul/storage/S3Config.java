package com.gurukul.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Built even when the bucket isn't configured - S3FileStorageService checks
 * properties.isConfigured() itself before ever calling S3, so app startup never fails on a
 * missing bucket. Credentials come from the default AWS credential chain (e.g. the EC2 instance
 * role), the same way AnthropicClientConfig's Bedrock backend does - no static AWS keys needed.
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class S3Config {

	@Bean
	public S3Client s3Client(StorageProperties properties) {
		String region = properties.s3Region() != null && !properties.s3Region().isBlank()
				? properties.s3Region()
				: "eu-north-1";
		return S3Client.builder().region(Region.of(region)).build();
	}

}
