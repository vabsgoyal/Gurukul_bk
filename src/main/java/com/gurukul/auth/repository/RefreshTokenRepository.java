package com.gurukul.auth.repository;

import com.gurukul.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	/** Locked so two concurrent refreshes with the same token can't both rotate it. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from RefreshToken t where t.tokenHash = :tokenHash")
	Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	@Modifying
	@Query("update RefreshToken t set t.revokedAt = :now, t.updatedAt = :now "
			+ "where t.credentialId = :credentialId and t.revokedAt is null")
	int revokeAllForCredential(@Param("credentialId") UUID credentialId, @Param("now") Instant now);

	@Modifying
	@Query("delete from RefreshToken t where t.expiresAt < :cutoff")
	int deleteExpiredBefore(@Param("cutoff") Instant cutoff);

}
