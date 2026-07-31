package com.gurukul.gamification.controller;

import com.gurukul.auth.security.AuthContext;
import com.gurukul.common.ApiResponse;
import com.gurukul.gamification.dto.GamificationDtos.GameProfileResponse;
import com.gurukul.gamification.service.GamificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Gamification", description = "Student XP, streaks, and (in later phases) leagues/houses/badges. "
		+ "Requires X-School-Id and Authorization headers.")
public class GamificationController {

	private final GamificationService gamificationService;

	public GamificationController(GamificationService gamificationService) {
		this.gamificationService = gamificationService;
	}

	@GetMapping("/api/v1/gamification/me")
	@Operation(summary = "My XP, level, and streak (student accounts only)")
	public ApiResponse<GameProfileResponse> me() {
		return ApiResponse.success(gamificationService.getMyProfile(AuthContext.current()));
	}

}
