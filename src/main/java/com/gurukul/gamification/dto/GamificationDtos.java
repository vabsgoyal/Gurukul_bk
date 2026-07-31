package com.gurukul.gamification.dto;

import com.gurukul.gamification.entity.StudentGameProfile;
import com.gurukul.gamification.service.LevelCalculator.LevelInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class GamificationDtos {

	@Getter @AllArgsConstructor
	@Schema(name = "GameProfileResponse")
	public static class GameProfileResponse {
		private long totalXp;
		private int level;
		private long xpIntoLevel;
		private long xpForNextLevel;
		private int currentStreakDays;
		private int longestStreakDays;

		public static GameProfileResponse from(StudentGameProfile profile, LevelInfo levelInfo) {
			return new GameProfileResponse(
					profile.getTotalXp(),
					levelInfo.level(),
					levelInfo.xpIntoLevel(),
					levelInfo.xpForNextLevel(),
					profile.getCurrentStreakDays(),
					profile.getLongestStreakDays());
		}
	}

}
