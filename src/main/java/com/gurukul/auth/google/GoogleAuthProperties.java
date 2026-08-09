package com.gurukul.auth.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.google")
public record GoogleAuthProperties(String clientId) {

	public boolean isConfigured() {
		return clientId != null && !clientId.isBlank();
	}
}
