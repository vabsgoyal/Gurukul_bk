package com.gurukul.gamification.repository;

import com.gurukul.gamification.entity.BattleAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BattleAnswerRepository extends JpaRepository<BattleAnswer, UUID> {

	List<BattleAnswer> findAllByRoomIdAndQuestionIndex(UUID roomId, int questionIndex);

	long countByRoomIdAndQuestionIndex(UUID roomId, int questionIndex);

	boolean existsByRoomIdAndQuestionIndexAndStudentId(UUID roomId, int questionIndex, UUID studentId);

}
