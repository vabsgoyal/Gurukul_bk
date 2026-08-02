package com.gurukul.gamification.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Only the current question's buzz winner ever gets to answer, so one row per (room, question). */
@Getter
@Setter
@Entity
@Table(name = "battle_answer", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"room_id", "question_index"})
})
public class BattleAnswer extends BaseEntity {

	@Column(name = "room_id", nullable = false)
	private UUID roomId;

	@Column(name = "question_index", nullable = false)
	private int questionIndex;

	@Column(name = "student_id", nullable = false)
	private UUID studentId;

	@Enumerated(EnumType.STRING)
	@Column(name = "selected_option", nullable = false)
	private QuizOption selectedOption;

	@Column(nullable = false)
	private boolean correct;

}
