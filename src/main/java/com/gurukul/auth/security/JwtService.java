package com.gurukul.auth.security;

import com.gurukul.auth.entity.Credential;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtService {

	/** Marks a short-lived "pick a profile" token - never accepted as an access token. */
	private static final String PURPOSE_CLAIM = "purpose";
	private static final String PROFILE_SELECTION = "otp-profile-selection";
	private static final long SELECTION_EXPIRATION_MILLIS = 10 * 60_000L;

	private final SecretKey key;
	private final long expirationMillis;

	public JwtService(
			@Value("${app.jwt.secret}") String secret,
			@Value("${app.jwt.expiration-minutes:1440}") long expirationMinutes) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expirationMillis = expirationMinutes * 60_000L;
	}

	/** When a token generated now would expire - reported to clients alongside the token. */
	public Instant accessTokenExpiresAt(Instant issuedAt) {
		return issuedAt.plusMillis(expirationMillis);
	}

	public String generateToken(Credential credential) {
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(credential.getOwnerId().toString())
				.claim("schoolId", credential.getSchoolId().toString())
				.claim("ownerType", credential.getOwnerType().name())
				.claim("role", credential.getRole().name())
				.claim("username", credential.getUsername())
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plusMillis(expirationMillis)))
				.signWith(key)
				.compact();
	}

	/**
	 * Issued after an OTP verifies for a phone shared by several profiles (e.g. siblings). Proves the
	 * caller owns the phone for a few minutes, so they can pick which profile to log in as.
	 */
	public String generateProfileSelectionToken(UUID schoolId, String phone) {
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(phone)
				.claim("schoolId", schoolId.toString())
				.claim(PURPOSE_CLAIM, PROFILE_SELECTION)
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plusMillis(SELECTION_EXPIRATION_MILLIS)))
				.signWith(key)
				.compact();
	}

	/** Returns the phone the selection token was issued for, if it's valid for this school. */
	public String parseProfileSelectionToken(String token, UUID schoolId) throws JwtException, IllegalArgumentException {
		Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
		if (!PROFILE_SELECTION.equals(claims.get(PURPOSE_CLAIM, String.class))
				|| !schoolId.toString().equals(claims.get("schoolId", String.class))) {
			throw new JwtException("Not a profile selection token for this school");
		}
		return claims.getSubject();
	}

	public AuthPrincipal parseToken(String token) throws JwtException, IllegalArgumentException {
		Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
		if (claims.get(PURPOSE_CLAIM) != null) {
			throw new JwtException("Not an access token");
		}
		return new AuthPrincipal(
				UUID.fromString(claims.getSubject()),
				OwnerType.valueOf(claims.get("ownerType", String.class)),
				Role.valueOf(claims.get("role", String.class)),
				UUID.fromString(claims.get("schoolId", String.class)),
				claims.get("username", String.class)
		);
	}

}
