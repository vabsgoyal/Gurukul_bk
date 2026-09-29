package com.gurukul.ai.controller;

import com.gurukul.ai.dto.QuizGeneratorDtos.AiQuizGenerationRequest;
import com.gurukul.ai.dto.QuizGeneratorDtos.AiQuizGenerationResponse;
import com.gurukul.ai.service.QuizGeneratorService;
import com.gurukul.auth.security.AuthContext;
import com.gurukul.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "AI quiz generator", description = "Drafts a quiz/test from a syllabus with a large language "
		+ "model. Returns a draft only - nothing is saved. Requires authentication and the X-School-Id header.")
public class QuizGeneratorController {

	private final QuizGeneratorService quizGeneratorService;

	@PostMapping("/api/v1/teachers/{teacherId}/ai/quiz-generator")
	@Operation(summary = "Generate a draft quiz for a class-section + subject",
			description = "A teacher may only generate for themselves (teacherId must be the caller) and for a "
					+ "section + subject they are assigned to; an admin may generate for any section of their "
					+ "school. The output is validated server-side (question count, marks total, 4 options per "
					+ "MCQ, answers present). 503 AI_UNAVAILABLE when the model is unavailable or can't produce a "
					+ "valid quiz after one retry.")
	public ApiResponse<AiQuizGenerationResponse> generate(@PathVariable UUID teacherId,
			@Valid @RequestBody AiQuizGenerationRequest request) {
		return ApiResponse.success(quizGeneratorService.generate(AuthContext.current(), teacherId, request));
	}

}
