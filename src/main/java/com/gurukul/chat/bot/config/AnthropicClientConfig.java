package com.gurukul.chat.bot.config;

import com.anthropic.bedrock.backends.BedrockBackend;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Built even when apiKey is blank - BotReplyService checks properties.isConfigured() itself before
 * ever calling the client, so this bean always exists and app startup never fails on a missing key.
 * When app.anthropic.backend=bedrock, AWS credentials (e.g. the EC2 instance role, resolved via the
 * default AWS credential chain) authorize the call instead of an Anthropic API key.
 */
@Configuration
@EnableConfigurationProperties(AnthropicProperties.class)
public class AnthropicClientConfig {

	@Bean
	public AnthropicClient anthropicClient(AnthropicProperties properties) {
		if (properties.isBedrock()) {
			return AnthropicOkHttpClient.builder().backend(BedrockBackend.fromEnv()).build();
		}
		return AnthropicOkHttpClient.builder().apiKey(properties.apiKey()).build();
	}

}
