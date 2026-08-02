package com.gurukul.gamification.controller;

import com.gurukul.auth.security.AuthContext;
import com.gurukul.common.ApiResponse;
import com.gurukul.gamification.dto.BattleRoomDtos.BattleRoomResponse;
import com.gurukul.gamification.dto.BattleRoomDtos.BattleRoomSummaryResponse;
import com.gurukul.gamification.dto.BattleRoomDtos.CreateBattleRoomRequest;
import com.gurukul.gamification.dto.BattleRoomDtos.JoinByCodeRequest;
import com.gurukul.gamification.dto.BattleRoomDtos.MatchBattleRoomRequest;
import com.gurukul.gamification.service.BattleRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Battle Rooms", description = "Live, multiplayer (2-5 students) fastest-buzz-first quiz battles, "
		+ "scoped to one class (any section) + one subject (Gamification Phase 4b). Buzzing and answering happen "
		+ "over STOMP (/app/battle-rooms/{roomId}/buzz, /app/battle-rooms/{roomId}/answer); these REST endpoints "
		+ "cover room creation/matching/lobby state. Requires X-School-Id and Authorization headers.")
public class BattleRoomController {

	private final BattleRoomService battleRoomService;

	public BattleRoomController(BattleRoomService battleRoomService) {
		this.battleRoomService = battleRoomService;
	}

	@PostMapping("/api/v1/gamification/battle-rooms")
	@Operation(summary = "Create a battle room for your class + a subject (student accounts only)")
	public ApiResponse<BattleRoomResponse> create(@Valid @RequestBody CreateBattleRoomRequest request) {
		return ApiResponse.success(battleRoomService.createRoom(AuthContext.current(), request), "Battle room created");
	}

	@PostMapping("/api/v1/gamification/battle-rooms/match")
	@Operation(summary = "Join an open room for your class + this subject, or create one if none is open")
	public ApiResponse<BattleRoomResponse> match(@Valid @RequestBody MatchBattleRoomRequest request) {
		return ApiResponse.success(battleRoomService.matchRoom(AuthContext.current(), request));
	}

	@PostMapping("/api/v1/gamification/battle-rooms/{id}/join")
	@Operation(summary = "Join a specific room by id (invite link)")
	public ApiResponse<BattleRoomResponse> join(@PathVariable UUID id) {
		return ApiResponse.success(battleRoomService.joinRoom(AuthContext.current(), id));
	}

	@PostMapping("/api/v1/gamification/battle-rooms/join-by-code")
	@Operation(summary = "Join a room via its 6-character shareable code")
	public ApiResponse<BattleRoomResponse> joinByCode(@Valid @RequestBody JoinByCodeRequest request) {
		return ApiResponse.success(battleRoomService.joinByCode(AuthContext.current(), request.getCode()));
	}

	@GetMapping("/api/v1/gamification/battle-rooms/{id}")
	@Operation(summary = "Room/lobby state - also the final leaderboard once completed")
	public ApiResponse<BattleRoomResponse> get(@PathVariable UUID id) {
		return ApiResponse.success(battleRoomService.getRoom(AuthContext.current(), id));
	}

	@GetMapping("/api/v1/gamification/battle-rooms")
	@Operation(summary = "Browse open rooms for your own class",
			description = "Every WAITING or ACTIVE room for your class (any section), optionally filtered to one "
					+ "subject. Only WAITING rooms can be joined - ACTIVE ones are listed so students can see a "
					+ "battle is in progress, but joining mid-battle isn't supported.")
	public ApiResponse<List<BattleRoomSummaryResponse>> list(@RequestParam(required = false) UUID subjectId) {
		return ApiResponse.success(battleRoomService.listBrowsableRooms(AuthContext.current(), subjectId));
	}

}
