package com.gurukul.gamification.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PracticeSessionStatus")
public enum PracticeSessionStatus {

	@Schema(description = "Still working through the question set")
	ACTIVE,

	@Schema(description = "Every question answered")
	COMPLETED

}
