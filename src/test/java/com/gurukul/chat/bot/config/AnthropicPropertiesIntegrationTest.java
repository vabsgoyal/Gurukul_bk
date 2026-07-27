package com.gurukul.chat.bot.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirms application.properties actually binds AnthropicProperties end-to-end (defaults included).
 * This is the test that would have caught the properties block being written into a plan but never
 * applied to the real file - a blank-but-bound apiKey is fine (BotReplyService handles it), but a
 * null model/effort/zero maxOutputTokens would silently break every real bot call.
 */
@SpringBootTest
class AnthropicPropertiesIntegrationTest {

	@Autowired
	private AnthropicProperties properties;

	@Test
	void bindsFromApplicationPropertiesWithSensibleDefaults() {
		assertThat(properties.apiKey()).isNotNull();
		assertThat(properties.model()).isEqualTo("claude-opus-5");
		assertThat(properties.effort()).isEqualTo("MEDIUM");
		assertThat(properties.maxOutputTokens()).isEqualTo(1024);
		assertThat(properties.maxToolIterations()).isEqualTo(5);
		assertThat(properties.historyWindow()).isEqualTo(20);
	}

}
