package com.gurukul.gamification.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ordered lowest to highest - declaration order is used directly for promotion/relegation
 * (ordinal() - 1 / + 1), so don't reorder without updating LeagueService.
 */
@Schema(name = "LeagueTier", description = "A student's current weekly league tier")
public enum LeagueTier {

	BRONZE, SILVER, GOLD, PLATINUM, DIAMOND, GURUKUL_MASTER

}
