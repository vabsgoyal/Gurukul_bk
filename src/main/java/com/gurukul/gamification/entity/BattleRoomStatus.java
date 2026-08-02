package com.gurukul.gamification.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "BattleRoomStatus")
public enum BattleRoomStatus {

	@Schema(description = "Join window open - waiting for players")
	WAITING,

	@Schema(description = "Battle in progress")
	ACTIVE,

	@Schema(description = "All questions answered - a winner has been decided")
	COMPLETED,

	@Schema(description = "Join window elapsed with only the creator present - nobody to battle")
	CANCELLED

}
