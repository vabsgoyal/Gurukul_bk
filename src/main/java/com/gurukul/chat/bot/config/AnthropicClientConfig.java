package com.gurukul.chat.bot.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Built even when apiKey is blank - BotReplyService checks that itself before ever calling the
 * client, so this bean always exists and app startup never fails on a missing key.
 */
@Configuration
@EnableConfigurationProperties(AnthropicProperties.class)
public class AnthropicClientConfig {

	@Bean
	public AnthropicClient anthropicClient(AnthropicProperties properties) {
		return AnthropicOkHttpClient.builder().apiKey(properties.apiKey()).build();
	}

}
