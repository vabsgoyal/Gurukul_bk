package com.gurukul.gamification.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * uq_battle_buzz_winner (room_id, question_index) is the fairness mechanism: every buzz is a
 * plain INSERT, whichever commits first wins the race, everyone else's insert fails the unique
 * constraint - see BattleRoomService.buzz().
 */
@Getter
@Setter
@Entity
@Table(name = "battle_buzz_winner", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"room_id", "question_index"})
})
public class BattleBuzzWinner extends BaseEntity {

	@Column(name = "room_id", nullable = false)
	private UUID roomId;

	@Column(name = "question_index", nullable = false)
	private int questionIndex;

	@Column(name = "student_id", nullable = false)
	private UUID studentId;

	@Column(name = "buzzed_at", nullable = false)
	private Instant buzzedAt;

}
