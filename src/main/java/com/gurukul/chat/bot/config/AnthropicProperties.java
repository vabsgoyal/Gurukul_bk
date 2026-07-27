package com.gurukul.chat.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * apiKey has no real default (see application.properties) - a blank key must fail loudly/gracefully
 * (BotReplyService checks isBlank() before ever calling the client), never silently "work" the way
 * app.jwt.secret's dev placeholder does. When backend is "bedrock", apiKey is not required - AWS
 * credentials (e.g. the EC2 instance role) authorize the call instead.
 */
@ConfigurationProperties(prefix = "app.anthropic")
public record AnthropicProperties(
		String backend,
		String apiKey,
		String model,
		String effort,
		int maxOutputTokens,
		int maxToolIterations,
		int historyWindow) {

	public boolean isBedrock() {
		return "bedrock".equalsIgnoreCase(backend);
	}

	public boolean isConfigured() {
		return isBedrock() || (apiKey != null && !apiKey.isBlank());
	}
}
