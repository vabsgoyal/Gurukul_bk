package com.gurukul.auth.service;

import com.gurukul.auth.dto.AuthDtos.LoginResponse;
import com.gurukul.auth.entity.Credential;
import com.gurukul.auth.entity.RefreshToken;
import com.gurukul.auth.repository.CredentialRepository;
import com.gurukul.auth.repository.RefreshTokenRepository;
import com.gurukul.auth.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Issues every login's token pair: a short-lived access JWT (24h) plus a refresh token that slides
 * forward on each use (7 days by default), so a user who opens the app at least once a window
 * never has to log in again. Refresh tokens rotate - each raw value works once.
 */
@Service
@Slf4j
public class SessionTokenService {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final String SESSION_EXPIRED = "Session expired - please log in again";

	/**
	 * A rotated token presented again this soon after rotation is most likely the app sending two
	 * refreshes at once, so it's just refused. Later than this, it looks like a stolen copy being
	 * replayed, and every session for that login is revoked.
	 */
	private static final Duration REUSE_GRACE = Duration.ofMinutes(1);

	private final RefreshTokenRepository refreshTokenRepository;
	private final CredentialRepository credentialRepository;
	private final JwtService jwtService;
	private final InactiveStaffGuard inactiveStaffGuard;
	private final Duration refreshTokenTtl;

	public SessionTokenService(
			RefreshTokenRepository refreshTokenRepository,
			CredentialRepository credentialRepository,
			JwtService jwtService,
			InactiveStaffGuard inactiveStaffGuard,
			@Value("${app.auth.refresh-token-days:7}") long refreshTokenDays) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.credentialRepository = credentialRepository;
		this.jwtService = jwtService;
		this.inactiveStaffGuard = inactiveStaffGuard;
		this.refreshTokenTtl = Duration.ofDays(refreshTokenDays);
	}

	@Transactional
	public LoginResponse issue(Credential credential) {
		Instant now = Instant.now();
		String rawRefreshToken = newRawToken();

		RefreshToken refreshToken = new RefreshToken();
		refreshToken.setSchoolId(credential.getSchoolId());
		refreshToken.setCredentialId(credential.getId());
		refreshToken.setTokenHash(hash(rawRefreshToken));
		refreshToken.setExpiresAt(now.plus(refreshTokenTtl));
		refreshTokenRepository.save(refreshToken);

		return new LoginResponse(
				jwtService.generateToken(credential), "Bearer", credential.getOwnerType(), credential.getOwnerId(),
				credential.getRole(), credential.getSchoolId(), credential.getUsername(),
				rawRefreshToken, jwtService.accessTokenExpiresAt(now), refreshToken.getExpiresAt());
	}

	/**
	 * An access token with no refresh token or session row behind it. Used for the placeholder login
	 * OTP verify returns alongside the profile picker (kept for app versions without the picker), so
	 * picking a different profile doesn't leave an unused 7-day session behind.
	 */
	public LoginResponse issueAccessOnly(Credential credential) {
		Instant now = Instant.now();
		return new LoginResponse(
				jwtService.generateToken(credential), "Bearer", credential.getOwnerType(), credential.getOwnerId(),
				credential.getRole(), credential.getSchoolId(), credential.getUsername(),
				null, jwtService.accessTokenExpiresAt(now), null);
	}

	/**
	 * Trades a refresh token for a new pair. Deliberately needs no access token or X-School-Id -
	 * the access token is usually the thing that just expired.
	 */
	@Transactional(noRollbackFor = BadCredentialsException.class)
	public LoginResponse refresh(String rawRefreshToken) {
		Instant now = Instant.now();
		RefreshToken refreshToken = refreshTokenRepository.findByTokenHashForUpdate(hash(rawRefreshToken))
				.orElseThrow(() -> new BadCredentialsException(SESSION_EXPIRED));

		if (refreshToken.getRevokedAt() != null) {
			if (refreshToken.getRevokedAt().plus(REUSE_GRACE).isBefore(now)) {
				int revoked = refreshTokenRepository.revokeAllForCredential(refreshToken.getCredentialId(), now);
				log.warn("Rotated refresh token reused for credential {} - revoked {} session(s)",
						refreshToken.getCredentialId(), revoked);
			}
			throw new BadCredentialsException(SESSION_EXPIRED);
		}
		if (refreshToken.getExpiresAt().isBefore(now)) {
			throw new BadCredentialsException(SESSION_EXPIRED);
		}

		Credential credential = credentialRepository.findById(refreshToken.getCredentialId())
				.orElseThrow(() -> new BadCredentialsException(SESSION_EXPIRED));
		refreshToken.setRevokedAt(now);
		if (!credential.isEnabled() || inactiveStaffGuard.isInactiveStaff(credential)) {
			throw new BadCredentialsException(SESSION_EXPIRED);
		}
		return issue(credential);
	}

	/** Logout: ends this device's session. Unknown or already-revoked tokens are fine - logout always succeeds. */
	@Transactional
	public void revoke(String rawRefreshToken) {
		if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
			return;
		}
		refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
				.filter(token -> token.getRevokedAt() == null)
				.ifPresent(token -> token.setRevokedAt(Instant.now()));
	}

	/** Expired rows are useless; keep a day's slack so a just-expired token still reads as "expired", not "unknown". */
	@Scheduled(cron = "${app.auth.refresh-token-cleanup-cron:0 30 3 * * *}")
	@Transactional
	public void deleteExpired() {
		int deleted = refreshTokenRepository.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(1)));
		if (deleted > 0) {
			log.info("Deleted {} expired refresh token(s)", deleted);
		}
	}

	private static String newRawToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static String hash(String rawToken) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
