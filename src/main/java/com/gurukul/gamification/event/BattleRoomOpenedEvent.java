package com.gurukul.gamification.event;

import java.time.Instant;
import java.util.UUID;

/** A new battle room is waiting for players. Carries everything the notification needs, so the listener reads nothing lazily. */
public record BattleRoomOpenedEvent(
		UUID schoolId, UUID roomId, String roomCode, String className, String academicYear,
		String subjectName, UUID creatorStudentId, String creatorName, Instant joinWindowEndsAt) {
}
