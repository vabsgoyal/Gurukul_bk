package com.gurukul.auth.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One device's login session. Rotated on every refresh: the used row is revoked and a new one
 * issued, so each raw token works once and an active user's 7-day window keeps sliding forward.
 */
@Getter
@Setter
@Entity
@Table(name = "refresh_token")
public class RefreshToken extends BaseEntity {

	@Column(name = "credential_id", nullable = false)
	private UUID credentialId;

	/** Hex SHA-256 of the raw token - the raw value is never stored. */
	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

}
