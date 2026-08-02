package com.gurukul.gamification.repository;

import com.gurukul.gamification.entity.BattleRoomParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BattleRoomParticipantRepository extends JpaRepository<BattleRoomParticipant, UUID> {

	List<BattleRoomParticipant> findAllByRoomIdOrderByJoinedAtAsc(UUID roomId);

	Optional<BattleRoomParticipant> findByRoomIdAndStudentId(UUID roomId, UUID studentId);

	long countByRoomId(UUID roomId);

	boolean existsByRoomIdAndStudentId(UUID roomId, UUID studentId);

}
