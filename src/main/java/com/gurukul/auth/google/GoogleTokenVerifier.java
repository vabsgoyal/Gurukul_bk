package com.gurukul.auth.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Verifies a Google-issued ID token's signature (against Google's own public JWKS, fetched and
 * cached by GoogleIdTokenVerifier itself), issuer, expiry, and audience (must match our own OAuth
 * web client ID - see app.google.client-id) - never trusts anything the client claims about who
 * signed in. Built even when unconfigured, same fail-at-call-time pattern as AnthropicClientConfig/
 * AttachmentS3Config: verify() throws immediately if there's no client ID to check against.
 */
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(GoogleAuthProperties.class)
public class GoogleTokenVerifier {

	private final GoogleAuthProperties properties;

	public record GoogleIdentity(String subject, String email, boolean emailVerified, String name) {
	}

	public GoogleIdentity verify(String idTokenString) {
		if (!properties.isConfigured()) {
			throw new IllegalStateException("Google Sign-In is not configured on this server");
		}
		try {
			GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
					.setAudience(List.of(properties.clientId()))
					.build();
			GoogleIdToken idToken = verifier.verify(idTokenString);
			if (idToken == null) {
				throw new BadCredentialsException("Invalid or expired Google sign-in token");
			}
			GoogleIdToken.Payload payload = idToken.getPayload();
			return new GoogleIdentity(
					payload.getSubject(),
					payload.getEmail(),
					Boolean.TRUE.equals(payload.getEmailVerified()),
					(String) payload.get("name"));
		} catch (GeneralSecurityException | java.io.IOException | IllegalArgumentException e) {
			throw new BadCredentialsException("Invalid or expired Google sign-in token", e);
		}
	}

}
