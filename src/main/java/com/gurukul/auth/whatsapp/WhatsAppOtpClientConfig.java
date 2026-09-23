package com.gurukul.auth.whatsapp;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Built even when the token is blank - WhatsAppOtpSender checks properties.isConfigured() itself
 * before ever calling through this client, so app startup never fails on missing WA-AKG config.
 * Same fail-open-at-call-time pattern as OpenRouterClientConfig.
 */
@Configuration
@EnableConfigurationProperties(WhatsAppOtpProperties.class)
public class WhatsAppOtpClientConfig {

	@Bean
	public RestClient whatsAppOtpRestClient(WhatsAppOtpProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(10));
		requestFactory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));

		return RestClient.builder()
				.baseUrl(properties.baseUrl())
				.requestFactory(requestFactory)
				// WA-AKG's server-to-server auth (per its swagger.json ApiKeyAuth scheme) is this
				// header, not a Bearer token.
				.defaultHeader("X-API-Key", properties.token())
				.build();
	}

}
