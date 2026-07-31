package com.gurukul.gamification.dto;

import com.gurukul.gamification.entity.LeagueTier;
import com.gurukul.gamification.entity.StudentGameProfile;
import com.gurukul.gamification.service.LevelCalculator.LevelInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

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

	@Getter @AllArgsConstructor
	@Schema(name = "LeaderboardEntryResponse")
	public static class LeaderboardEntryResponse {
		private int rank;
		private UUID studentId;
		private String name;
		private long weeklyXp;
		private boolean isYou;
	}

	@Getter @AllArgsConstructor
	@Schema(name = "LeaderboardResponse")
	public static class LeaderboardResponse {
		private LeagueTier tier;
		private String classSectionLabel;
		private List<LeaderboardEntryResponse> entries;
		private int yourRank;
		private int currentStreakDays;
		private int longestStreakDays;
	}

}
