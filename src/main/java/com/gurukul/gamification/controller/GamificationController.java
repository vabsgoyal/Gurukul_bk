package com.gurukul.gamification.controller;

import com.gurukul.auth.security.AuthContext;
import com.gurukul.common.ApiResponse;
import com.gurukul.gamification.dto.GamificationDtos.GameProfileResponse;
import com.gurukul.gamification.dto.GamificationDtos.LeaderboardResponse;
import com.gurukul.gamification.service.GamificationService;
import com.gurukul.gamification.service.LeagueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Gamification", description = "Student XP, streaks, league leaderboards, and (in later phases) "
		+ "houses/badges. Requires X-School-Id and Authorization headers.")
public class GamificationController {

	private final GamificationService gamificationService;
	private final LeagueService leagueService;

	public GamificationController(GamificationService gamificationService, LeagueService leagueService) {
		this.gamificationService = gamificationService;
		this.leagueService = leagueService;
	}

	@GetMapping("/api/v1/gamification/me")
	@Operation(summary = "My XP, level, and streak (student accounts only)")
	public ApiResponse<GameProfileResponse> me() {
		return ApiResponse.success(gamificationService.getMyProfile(AuthContext.current()));
	}

	@GetMapping("/api/v1/gamification/leaderboard")
	@Operation(summary = "My league leaderboard - everyone in my class-section currently in my tier, ranked by this week's XP")
	public ApiResponse<LeaderboardResponse> leaderboard() {
		return ApiResponse.success(leagueService.getLeaderboard(AuthContext.current()));
	}

}
