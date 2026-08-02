package com.gurukul.gamification.repository;

import com.gurukul.gamification.entity.ChallengeStatus;
import com.gurukul.gamification.entity.QuizChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizChallengeRepository extends JpaRepository<QuizChallenge, UUID> {

	Optional<QuizChallenge> findByIdAndSchoolId(UUID id, UUID schoolId);

	@Query("SELECT c FROM QuizChallenge c WHERE c.schoolId = :schoolId AND "
			+ "(c.challengerStudentId = :studentId OR c.opponentStudentId = :studentId) "
			+ "ORDER BY c.createdAt DESC")
	List<QuizChallenge> findAllForStudent(@Param("schoolId") UUID schoolId, @Param("studentId") UUID studentId);

	List<QuizChallenge> findAllByStatusAndCreatedAtBefore(ChallengeStatus status, Instant cutoff);

}
