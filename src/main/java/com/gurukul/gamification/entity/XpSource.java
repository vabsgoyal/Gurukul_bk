package com.gurukul.gamification.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "XpSource", description = "What earned a student XP")
public enum XpSource {

	@Schema(description = "Marked present/late/half-day for a school day")
	ATTENDANCE,

	@Schema(description = "Assessment result recorded")
	ASSESSMENT,

	@Schema(description = "Won a Gurukul Arena quiz battle")
	QUIZ_WIN,

	@Schema(description = "Won a live Battle Room")
	BATTLE_ROOM_WIN,

	@Schema(description = "Teacher-awarded class participation")
	PARTICIPATION,

	@Schema(description = "Streak milestone bonus")
	STREAK_BONUS,

	@Schema(description = "One-off manual adjustment")
	MANUAL

}
