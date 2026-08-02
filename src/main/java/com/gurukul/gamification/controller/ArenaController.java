package com.gurukul.gamification.controller;

import com.gurukul.auth.security.AuthContext;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.ApiResponse;
import com.gurukul.gamification.dto.ArenaDtos.ChallengeDetailResponse;
import com.gurukul.gamification.dto.ArenaDtos.ChallengeSummaryResponse;
import com.gurukul.gamification.dto.ArenaDtos.CreateChallengeRequest;
import com.gurukul.gamification.dto.ArenaDtos.CreateQuizQuestionRequest;
import com.gurukul.gamification.dto.ArenaDtos.QuizQuestionResponse;
import com.gurukul.gamification.dto.ArenaDtos.SubmitAnswerRequest;
import com.gurukul.gamification.dto.ArenaDtos.SubmitAnswerResponse;
import com.gurukul.gamification.service.ArenaService;
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
@Tag(name = "Gurukul Arena", description = "Async 1v1 quiz challenges between classmates (Gamification Phase 4a). "
		+ "Live/synchronized class-wide quizzes are a future phase. Requires X-School-Id and Authorization headers.")
public class ArenaController {

	private final ArenaService arenaService;

	public ArenaController(ArenaService arenaService) {
		this.arenaService = arenaService;
	}

	@PostMapping("/api/v1/gamification/arena/questions")
	@Operation(summary = "Author a quiz question (teacher or admin only)")
	public ApiResponse<QuizQuestionResponse> createQuestion(@Valid @RequestBody CreateQuizQuestionRequest request) {
		return ApiResponse.success(arenaService.createQuestion(AuthContext.current(), request), "Question added");
	}

	@GetMapping("/api/v1/gamification/arena/questions")
	@Operation(summary = "List a subject's question bank (teacher or admin only)")
	public ApiResponse<List<QuizQuestionResponse>> listQuestions(@RequestParam UUID subjectId) {
		return ApiResponse.success(arenaService.listQuestions(AuthContext.current(), subjectId));
	}

	@PostMapping("/api/v1/gamification/arena/challenges")
	@Operation(summary = "Challenge a classmate to a 1v1 quiz (student accounts only)")
	public ApiResponse<ChallengeSummaryResponse> createChallenge(@Valid @RequestBody CreateChallengeRequest request) {
		AuthPrincipal principal = AuthContext.current();
		return ApiResponse.success(arenaService.createChallenge(principal, request), "Challenge sent");
	}

	@GetMapping("/api/v1/gamification/arena/challenges")
	@Operation(summary = "My challenges, as challenger or opponent")
	public ApiResponse<List<ChallengeSummaryResponse>> myChallenges() {
		return ApiResponse.success(arenaService.listMyChallenges(AuthContext.current()));
	}

	@GetMapping("/api/v1/gamification/arena/challenges/{id}")
	@Operation(summary = "Challenge detail - the (unrevealed) question set and my answered progress")
	public ApiResponse<ChallengeDetailResponse> getChallenge(@PathVariable UUID id) {
		return ApiResponse.success(arenaService.getChallenge(AuthContext.current(), id));
	}

	@PostMapping("/api/v1/gamification/arena/challenges/{id}/answers")
	@Operation(summary = "Submit one answer - auto-resolves the challenge once both sides finish")
	public ApiResponse<SubmitAnswerResponse> submitAnswer(@PathVariable UUID id, @Valid @RequestBody SubmitAnswerRequest request) {
		return ApiResponse.success(arenaService.submitAnswer(AuthContext.current(), id, request));
	}

}
