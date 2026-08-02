package com.gurukul.gamification.controller;

import com.gurukul.auth.security.AuthContext;
import com.gurukul.common.ApiResponse;
import com.gurukul.gamification.dto.PracticeDtos.CreatePracticeSessionRequest;
import com.gurukul.gamification.dto.PracticeDtos.PracticeSessionResponse;
import com.gurukul.gamification.dto.PracticeDtos.SubmitPracticeAnswerRequest;
import com.gurukul.gamification.dto.PracticeDtos.SubmitPracticeAnswerResponse;
import com.gurukul.gamification.service.PracticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Practice Mode", description = "Solo, no-stakes practice sessions - pick a subject and work through "
		+ "questions from the same bank Arena/Battle Rooms use, at your own pace. No XP is awarded; this is prep, "
		+ "not competition. Requires X-School-Id and Authorization headers.")
public class PracticeController {

	private final PracticeService practiceService;

	public PracticeController(PracticeService practiceService) {
		this.practiceService = practiceService;
	}

	@PostMapping("/api/v1/gamification/practice/sessions")
	@Operation(summary = "Start a practice session for a subject (student accounts only)")
	public ApiResponse<PracticeSessionResponse> create(@Valid @RequestBody CreatePracticeSessionRequest request) {
		return ApiResponse.success(practiceService.createSession(AuthContext.current(), request), "Practice session started");
	}

	@GetMapping("/api/v1/gamification/practice/sessions/{id}")
	@Operation(summary = "Session detail - questions and my progress so far")
	public ApiResponse<PracticeSessionResponse> get(@PathVariable UUID id) {
		return ApiResponse.success(practiceService.getSession(AuthContext.current(), id));
	}

	@PostMapping("/api/v1/gamification/practice/sessions/{id}/answers")
	@Operation(summary = "Submit one answer - immediate correct/incorrect feedback")
	public ApiResponse<SubmitPracticeAnswerResponse> submitAnswer(@PathVariable UUID id, @Valid @RequestBody SubmitPracticeAnswerRequest request) {
		return ApiResponse.success(practiceService.submitAnswer(AuthContext.current(), id, request));
	}

}
