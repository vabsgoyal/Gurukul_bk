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

@Getter
@Setter
@Entity
@Table(name = "battle_room_participant", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"room_id", "student_id"})
})
public class BattleRoomParticipant extends BaseEntity {

	@Column(name = "room_id", nullable = false)
	private UUID roomId;

	@Column(name = "student_id", nullable = false)
	private UUID studentId;

	@Column(name = "correct_count", nullable = false)
	private int correctCount;

	@Column(name = "joined_at", nullable = false)
	private Instant joinedAt;

}
