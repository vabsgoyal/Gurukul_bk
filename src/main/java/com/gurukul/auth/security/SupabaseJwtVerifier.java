package com.gurukul.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Verifies Supabase-issued phone-OTP access tokens (ES256, verified via the project's public
 * JWKS) as a second accepted credential alongside this app's own HMAC-signed JWTs (see
 * {@link JwtService}) - used only by the one-time session exchange in SupabaseAuthController,
 * not by the general request filter, so nothing else in the request pipeline changes.
 */
@Component
public class SupabaseJwtVerifier {

	private final String issuer;
	private final JwtDecoder decoder;

	public SupabaseJwtVerifier(@Value("${app.auth.supabase.project-url:}") String projectUrl) {
		if (projectUrl == null || projectUrl.isBlank()) {
			this.issuer = null;
			this.decoder = null;
		} else {
			String base = projectUrl.replaceAll("/+$", "");
			this.issuer = base + "/auth/v1";
			this.decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json").build();
		}
	}

	public boolean isConfigured() {
		return decoder != null;
	}

	public record VerifiedSupabaseToken(UUID supabaseUserId, String phone) {
	}

	/** @throws BadCredentialsException if unconfigured, malformed, wrong issuer, or an invalid/expired signature. */
	public VerifiedSupabaseToken verify(String token) {
		if (decoder == null) {
			throw new BadCredentialsException("Supabase phone login is not configured");
		}
		Jwt jwt;
		try {
			jwt = decoder.decode(token);
		} catch (JwtException ex) {
			throw new BadCredentialsException("Invalid Supabase token", ex);
		}
		if (jwt.getIssuer() == null || !issuer.equals(jwt.getIssuer().toString())) {
			throw new BadCredentialsException("Token issuer does not match the configured Supabase project");
		}
		String phone = jwt.getClaimAsString("phone");
		if (phone == null || phone.isBlank()) {
			throw new BadCredentialsException("Supabase token has no verified phone number");
		}
		return new VerifiedSupabaseToken(UUID.fromString(jwt.getSubject()), phone);
	}

}
