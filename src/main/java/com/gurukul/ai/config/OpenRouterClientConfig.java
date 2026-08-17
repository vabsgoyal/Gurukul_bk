package com.gurukul.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Built even when apiKey is blank - AiChatService checks properties.isConfigured() itself before
 * ever calling through this client, so this bean always exists and app startup never fails on a
 * missing key. Same fail-open-at-call-time pattern as AnthropicClientConfig and AttachmentS3Config.
 *
 * <p>The read timeout is generous (default 60s): a reasoning model answering a long question can
 * legitimately take tens of seconds, and a premature client-side timeout bills the tokens without
 * returning the answer.
 */
@Configuration
@EnableConfigurationProperties(OpenRouterProperties.class)
public class OpenRouterClientConfig {

	@Bean
	public RestClient openRouterRestClient(OpenRouterProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(10));
		requestFactory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));

		return RestClient.builder()
				.baseUrl(properties.baseUrl())
				.requestFactory(requestFactory)
				// A blank key still builds a valid client - the Authorization header is simply
				// wrong, and OpenRouter answers 401, which OpenRouterClient maps to a friendly
				// "not configured correctly" message. isConfigured() short-circuits before that.
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
				// Optional OpenRouter attribution headers - they surface the app name on
				// OpenRouter's dashboard/leaderboard and carry no auth meaning.
				.defaultHeader("HTTP-Referer", properties.referer())
				.defaultHeader("X-OpenRouter-Title", properties.appTitle())
				.build();
	}

}
