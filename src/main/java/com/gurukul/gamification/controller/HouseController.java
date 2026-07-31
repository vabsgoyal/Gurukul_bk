package com.gurukul.gamification.controller;

import com.gurukul.auth.security.AuthContext;
import com.gurukul.common.ApiResponse;
import com.gurukul.gamification.dto.HouseDtos.AwardSpotRecognitionRequest;
import com.gurukul.gamification.dto.HouseDtos.CreateHouseRequest;
import com.gurukul.gamification.dto.HouseDtos.HouseResponse;
import com.gurukul.gamification.dto.HouseDtos.HouseWarsResponse;
import com.gurukul.gamification.service.HouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Houses", description = "The school-wide team layer (Gamification Phase 3). Visible to every "
		+ "role - students, teachers, and admins all see the same House Wars board. Requires X-School-Id "
		+ "and Authorization headers.")
public class HouseController {

	private final HouseService houseService;

	public HouseController(HouseService houseService) {
		this.houseService = houseService;
	}

	@GetMapping("/api/v1/houses")
	@Operation(summary = "List this school's houses")
	public ApiResponse<List<HouseResponse>> list() {
		return ApiResponse.success(houseService.listHouses(AuthContext.current()));
	}

	@PostMapping("/api/v1/houses")
	@Operation(summary = "Create a house (admin only)")
	public ApiResponse<HouseResponse> create(@Valid @RequestBody CreateHouseRequest request) {
		return ApiResponse.success(houseService.createHouse(AuthContext.current(), request), "House created");
	}

	@GetMapping("/api/v1/houses/wars")
	@Operation(summary = "House Wars: standings (XP + spot recognition) and the recent recognition feed")
	public ApiResponse<HouseWarsResponse> wars() {
		return ApiResponse.success(houseService.getHouseWars(AuthContext.current()));
	}

	@PostMapping("/api/v1/houses/spot-recognition")
	@Operation(summary = "Award a student spot-recognition house points (teacher or admin only)")
	public ApiResponse<Void> awardSpotRecognition(@Valid @RequestBody AwardSpotRecognitionRequest request) {
		houseService.awardSpotRecognition(AuthContext.current(), request);
		return ApiResponse.success(null, "Recognition awarded");
	}

}
