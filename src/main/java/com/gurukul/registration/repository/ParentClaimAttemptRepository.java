package com.gurukul.registration.repository;

import com.gurukul.registration.entity.ParentClaimAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ParentClaimAttemptRepository extends JpaRepository<ParentClaimAttempt, UUID> {

	Optional<ParentClaimAttempt> findBySchoolIdAndStudentRegistrationNumber(UUID schoolId, String studentRegistrationNumber);

}
