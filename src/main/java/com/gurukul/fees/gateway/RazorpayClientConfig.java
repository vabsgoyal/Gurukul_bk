package com.gurukul.fees.gateway;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Built even when the credentials are blank - RazorpayClient checks properties.isConfigured() before
 * ever calling through, so this bean always exists and startup never depends on a secret being
 * present. Same fail-open-at-call-time pattern as OpenRouterClientConfig and AttachmentS3Config.
 *
 * <p>Razorpay's REST API authenticates with HTTP Basic (keyId as username, keySecret as password),
 * so the header can be fixed at build time rather than per request.
 *
 * <p>Timeouts are deliberately short. Order creation happens while a student is staring at a
 * spinner, and a hung gateway call should surface as a retryable error quickly rather than holding
 * a request thread for a minute.
 */
@Configuration
@EnableConfigurationProperties(RazorpayProperties.class)
public class RazorpayClientConfig {

	@Bean
	public RestClient razorpayRestClient(RazorpayProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(10));
		requestFactory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));

		String credentials = properties.keyId() + ":" + properties.keySecret();
		String basicAuth = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

		return RestClient.builder()
				.baseUrl(properties.baseUrl())
				.requestFactory(requestFactory)
				// Blank credentials still build a valid client - Razorpay simply answers 401, which
				// RazorpayClient maps to a readable message. isConfigured() short-circuits first.
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
				.build();
	}

}
