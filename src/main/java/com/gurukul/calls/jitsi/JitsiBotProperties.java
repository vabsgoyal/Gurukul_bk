package com.gurukul.calls.jitsi;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * chromeBinary/chromedriverPath are optional overrides for non-standard install locations (e.g.
 * Alpine's chromium-chromedriver package puts these somewhere other than Selenium Manager's
 * default expectations) - blank means "let Selenium figure it out". profileDir must point at a
 * Chrome user-data-dir where a human has already completed the one-time Jitsi login (see
 * docs/jitsi-bot-setup.md); a blank value is a safe no-op, same pattern as AnthropicProperties.
 */
@ConfigurationProperties(prefix = "app.jitsi.bot")
public record JitsiBotProperties(
		boolean enabled,
		String baseUrl,
		String profileDir,
		String chromeBinary,
		String chromedriverPath,
		int warmTimeoutSeconds) {

	public boolean isConfigured() {
		return enabled && profileDir != null && !profileDir.isBlank();
	}
}
