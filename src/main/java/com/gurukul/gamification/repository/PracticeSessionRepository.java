package com.gurukul.gamification.repository;

import com.gurukul.gamification.entity.PracticeSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PracticeSessionRepository extends JpaRepository<PracticeSession, UUID> {

	Optional<PracticeSession> findByIdAndSchoolIdAndStudentId(UUID id, UUID schoolId, UUID studentId);

}
