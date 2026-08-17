package com.gurukul.calls.googlemeet;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A distinct OAuth client from app.google.client-id (Google Sign-In, ID-token verification only) -
 * this one needs a client SECRET and the calendar.events scope, since it performs a server-side
 * authorization-code exchange to get a refresh token, not just verifying an already-issued ID
 * token. See docs/google-meet-setup.md for the one-time Google Cloud Console setup.
 */
@ConfigurationProperties(prefix = "app.google.meet")
public record GoogleMeetProperties(String clientId, String clientSecret, String redirectUri) {

	public boolean isConfigured() {
		return clientId != null && !clientId.isBlank()
				&& clientSecret != null && !clientSecret.isBlank()
				&& redirectUri != null && !redirectUri.isBlank();
	}
}
