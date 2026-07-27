package com.gurukul.chat.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * apiKey has no real default (see application.properties) - a blank key must fail loudly/gracefully
 * (BotReplyService checks isBlank() before ever calling the client), never silently "work" the way
 * app.jwt.secret's dev placeholder does.
 */
@ConfigurationProperties(prefix = "app.anthropic")
public record AnthropicProperties(
		String apiKey,
		String model,
		String effort,
		int maxOutputTokens,
		int maxToolIterations,
		int historyWindow) {
}
