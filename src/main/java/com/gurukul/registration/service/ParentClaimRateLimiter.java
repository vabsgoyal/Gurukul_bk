package com.gurukul.registration.service;

import com.gurukul.registration.entity.ParentClaimAttempt;
import com.gurukul.registration.repository.ParentClaimAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks failed parent-registration claim attempts, in its own REQUIRES_NEW transaction so the
 * counter still persists even though the caller (RegistrationService.verifyParentClaim) throws and
 * rolls back its own transaction on every failed attempt - without this, the increment would be
 * rolled back right along with it and the rate limit would never actually engage.
 */
@Service
@RequiredArgsConstructor
public class ParentClaimRateLimiter {

	private static final int MAX_ATTEMPTS = 5;
	private static final long LOCKOUT_MINUTES = 15;

	private final ParentClaimAttemptRepository parentClaimAttemptRepository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean isLocked(UUID schoolId, String studentRollNumber) {
		return parentClaimAttemptRepository.findBySchoolIdAndStudentRollNumber(schoolId, studentRollNumber)
				.map(a -> a.getLockedUntil() != null && a.getLockedUntil().isAfter(Instant.now()))
				.orElse(false);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordFailure(UUID schoolId, String studentRollNumber) {
		ParentClaimAttempt attempt = parentClaimAttemptRepository
				.findBySchoolIdAndStudentRollNumber(schoolId, studentRollNumber)
				.orElseGet(() -> {
					ParentClaimAttempt a = new ParentClaimAttempt();
					a.setSchoolId(schoolId);
					a.setStudentRollNumber(studentRollNumber);
					a.setAttemptCount(0);
					return a;
				});
		attempt.setAttemptCount(attempt.getAttemptCount() + 1);
		if (attempt.getAttemptCount() >= MAX_ATTEMPTS) {
			attempt.setLockedUntil(Instant.now().plusSeconds(LOCKOUT_MINUTES * 60));
		}
		parentClaimAttemptRepository.save(attempt);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordSuccess(UUID schoolId, String studentRollNumber) {
		parentClaimAttemptRepository.findBySchoolIdAndStudentRollNumber(schoolId, studentRollNumber)
				.ifPresent(parentClaimAttemptRepository::delete);
	}

}
