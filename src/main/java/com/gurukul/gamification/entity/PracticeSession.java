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
 * Solo, no-stakes prep: a student works through a fixed question set (same quiz_question bank
 * Arena/Battle Rooms use) at their own pace. Deliberately kept out of the XP economy - this is
 * practice, not competition. question_ids is a fixed, comma-joined list, same convention as
 * quiz_challenge/battle_room.
 */
@Getter
@Setter
@Entity
@Table(name = "practice_session")
public class PracticeSession extends BaseEntity {

	@Column(name = "student_id", nullable = false)
	private UUID studentId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "subject_id", nullable = false)
	private Subject subject;

	@Column(name = "question_ids", nullable = false, length = 500)
	private String questionIds;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PracticeSessionStatus status;

	public List<UUID> questionIdList() {
		return Stream.of(questionIds.split(",")).map(UUID::fromString).toList();
	}

	public static String joinQuestionIds(List<UUID> ids) {
		return ids.stream().map(UUID::toString).collect(Collectors.joining(","));
	}

}
