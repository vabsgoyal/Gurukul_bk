package com.gurukul.notifications.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Pushes are sent inline from the action that triggers them (a chat message, an announcement), so
 * a slow or unreachable Expo must not hang that request - bounded timeouts, same as
 * WhatsAppOtpClientConfig.
 */
@Configuration
public class ExpoPushClientConfig {

	@Bean
	public RestClient expoPushRestClient() {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(5));
		requestFactory.setReadTimeout(Duration.ofSeconds(10));
		return RestClient.builder().requestFactory(requestFactory).build();
	}

}
