package com.gurukul.gamification.entity;

import com.gurukul.academics.entity.Subject;
import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Async 1v1 only for this first cut of the Arena (Phase 4a) - each side answers in their own
 * time, no live/synchronized session. questionIds is a fixed, comma-joined list decided once at
 * creation so both sides answer the exact same set; a dedicated join table felt like overkill
 * for what's a small, immutable, ordered list read as a whole every time.
 */
@Getter
@Setter
@Entity
@Table(name = "quiz_challenge")
public class QuizChallenge extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "subject_id", nullable = false)
	private Subject subject;

	@Column(name = "challenger_student_id", nullable = false)
	private UUID challengerStudentId;

	@Column(name = "opponent_student_id", nullable = false)
	private UUID opponentStudentId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ChallengeStatus status;

	@Column(name = "winner_student_id")
	private UUID winnerStudentId;

	@Column(name = "question_ids", nullable = false, length = 500)
	private String questionIds;

	public List<UUID> questionIdList() {
		return Stream.of(questionIds.split(",")).map(UUID::fromString).toList();
	}

	public static String joinQuestionIds(List<UUID> ids) {
		return ids.stream().map(UUID::toString).collect(Collectors.joining(","));
	}

}
