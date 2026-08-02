package com.gurukul.gamification.repository;

import com.gurukul.gamification.entity.QuizAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, UUID> {

	List<QuizAnswer> findAllByChallengeIdAndStudentId(UUID challengeId, UUID studentId);

	boolean existsByChallengeIdAndStudentIdAndQuestionId(UUID challengeId, UUID studentId, UUID questionId);

}
