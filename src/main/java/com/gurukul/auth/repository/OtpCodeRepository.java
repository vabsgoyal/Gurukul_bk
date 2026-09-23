package com.gurukul.auth.repository;

import com.gurukul.auth.entity.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {

	Optional<OtpCode> findFirstBySchoolIdAndPhoneAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
			UUID schoolId, String phone, Instant now);

}
