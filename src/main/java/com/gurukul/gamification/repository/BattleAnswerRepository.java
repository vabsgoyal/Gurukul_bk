package com.gurukul.gamification.repository;

import com.gurukul.gamification.entity.BattleAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BattleAnswerRepository extends JpaRepository<BattleAnswer, UUID> {

	Optional<BattleAnswer> findByRoomIdAndQuestionIndex(UUID roomId, int questionIndex);

	boolean existsByRoomIdAndQuestionIndex(UUID roomId, int questionIndex);

}
