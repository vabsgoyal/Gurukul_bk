package com.gurukul.gamification.repository;

import com.gurukul.gamification.entity.BattleBuzzWinner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BattleBuzzWinnerRepository extends JpaRepository<BattleBuzzWinner, UUID> {

	Optional<BattleBuzzWinner> findByRoomIdAndQuestionIndex(UUID roomId, int questionIndex);

}
