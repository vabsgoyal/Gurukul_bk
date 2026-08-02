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

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Gamification Phase 4b: a live, multiplayer (2-5 students) battle scoped to one class - any
 * section - and one subject. question_ids is a fixed, comma-joined list decided once when the
 * room activates, same convention as QuizChallenge.questionIds.
 */
@Getter
@Setter
@Entity
@Table(name = "battle_room")
public class BattleRoom extends BaseEntity {

	@Column(name = "class_name", nullable = false)
	private String className;

	@Column(name = "academic_year", nullable = false)
	private String academicYear;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "subject_id", nullable = false)
	private Subject subject;

	@Column(name = "created_by_student_id", nullable = false)
	private UUID createdByStudentId;

	@Column(name = "room_code", length = 6)
	private String roomCode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private BattleRoomStatus status;

	@Column(name = "min_players", nullable = false)
	private int minPlayers;

	@Column(name = "max_players", nullable = false)
	private int maxPlayers;

	@Column(name = "join_window_seconds", nullable = false)
	private int joinWindowSeconds;

	@Column(name = "question_count", nullable = false)
	private int questionCount;

	@Column(name = "question_ids", length = 500)
	private String questionIds;

	@Column(name = "current_question_index", nullable = false)
	private int currentQuestionIndex;

	@Column(name = "question_started_at")
	private Instant questionStartedAt;

	@Column(name = "winner_student_id")
	private UUID winnerStudentId;

	public List<UUID> questionIdList() {
		if (questionIds == null || questionIds.isBlank()) {
			return List.of();
		}
		return Stream.of(questionIds.split(",")).map(UUID::fromString).toList();
	}

	public static String joinQuestionIds(List<UUID> ids) {
		return ids.stream().map(UUID::toString).collect(Collectors.joining(","));
	}

}
