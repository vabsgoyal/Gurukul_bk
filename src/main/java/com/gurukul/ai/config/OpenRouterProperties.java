package com.gurukul.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * apiKey has no real default (see application.properties) - a blank key must fail loudly/gracefully
 * (AiChatService checks isConfigured() before ever calling the client), never silently "work" the
 * way app.jwt.secret's dev placeholder does. Same pattern as AnthropicProperties.
 *
 * <p>model is an OpenRouter slug in {@code <vendor>/<model>} form. Note the family-dependent
 * punctuation: the Claude 5 family has no dot ("anthropic/claude-sonnet-5") while 4.x does
 * ("anthropic/claude-haiku-4.5") - verify the exact slug on https://openrouter.ai/models rather
 * than deriving it, since a wrong slug fails at call time, not at startup.
 */
@ConfigurationProperties(prefix = "app.openrouter")
public record OpenRouterProperties(
		String baseUrl,
		String apiKey,
		String model,
		int maxOutputTokens,
		double temperature,
		int timeoutSeconds,
		String referer,
		String appTitle,
		int historyWindow,
		int maxRequestsPerUserPerHour,
		int maxResourceContextChars) {

	public boolean isConfigured() {
		return apiKey != null && !apiKey.isBlank();
	}

}
