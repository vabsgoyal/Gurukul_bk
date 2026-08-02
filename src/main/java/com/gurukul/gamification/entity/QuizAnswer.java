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

@Getter
@Setter
@Entity
@Table(name = "quiz_answer", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"challenge_id", "student_id", "question_id"})
})
public class QuizAnswer extends BaseEntity {

	@Column(name = "challenge_id", nullable = false)
	private UUID challengeId;

	@Column(name = "student_id", nullable = false)
	private UUID studentId;

	@Column(name = "question_id", nullable = false)
	private UUID questionId;

	@Enumerated(EnumType.STRING)
	@Column(name = "selected_option", nullable = false)
	private QuizOption selectedOption;

	@Column(nullable = false)
	private boolean correct;

}
