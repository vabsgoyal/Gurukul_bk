package com.gurukul.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI quiz generator limits. The provider, key, model and timeout are the Academic Helper's
 * (app.openrouter.*) - only what differs for a whole generated quiz lives here.
 *
 * @param maxOutputTokens output cap for one generation call. A 30-question quiz with one-line
 *        explanations is roughly 3000 tokens; the cap also bounds latency against the provider
 *        read timeout.
 * @param maxQuestions the most questions one request may ask for.
 */
@ConfigurationProperties(prefix = "app.quizgen")
public record QuizGenProperties(int maxOutputTokens, int maxQuestions) {

	public QuizGenProperties {
		if (maxOutputTokens <= 0) {
			maxOutputTokens = 4000;
		}
		if (maxQuestions <= 0) {
			maxQuestions = 30;
		}
	}

	@Configuration
	@EnableConfigurationProperties(QuizGenProperties.class)
	static class Registration {
	}

}
