package com.gurukul.gamification.event;

import java.util.UUID;

/** A student challenged a classmate. */
public record QuizChallengeCreatedEvent(
		UUID schoolId, UUID challengeId, UUID opponentStudentId, String challengerName, String subjectName) {
}
