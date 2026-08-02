package com.gurukul.gamification.repository;

import com.gurukul.gamification.entity.PracticeAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PracticeAnswerRepository extends JpaRepository<PracticeAnswer, UUID> {

	List<PracticeAnswer> findAllBySessionId(UUID sessionId);

	boolean existsBySessionIdAndQuestionId(UUID sessionId, UUID questionId);

}
