package com.gurukul.gamification.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ChallengeStatus")
public enum ChallengeStatus {

	@Schema(description = "Waiting for one or both sides to finish answering")
	ACTIVE,

	@Schema(description = "Both sides answered every question - a winner (or draw) has been decided")
	COMPLETED,

	@Schema(description = "Not completed within the time limit")
	EXPIRED

}
