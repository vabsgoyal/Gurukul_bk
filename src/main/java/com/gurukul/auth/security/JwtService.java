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

	private final SecretKey key;
	private final long expirationMillis;

	public JwtService(
			@Value("${app.jwt.secret}") String secret,
			@Value("${app.jwt.expiration-minutes:1440}") long expirationMinutes) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expirationMillis = expirationMinutes * 60_000L;
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

	public AuthPrincipal parseToken(String token) throws JwtException, IllegalArgumentException {
		Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
		return new AuthPrincipal(
				UUID.fromString(claims.getSubject()),
				OwnerType.valueOf(claims.get("ownerType", String.class)),
				Role.valueOf(claims.get("role", String.class)),
				UUID.fromString(claims.get("schoolId", String.class)),
				claims.get("username", String.class)
		);
	}

}
