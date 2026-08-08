package com.gurukul.teachers.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.teachers.dto.AiQuizGenerationRequest;
import com.gurukul.teachers.dto.AiQuizGenerationResponse;
import com.gurukul.teachers.service.TeacherAiQuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teachers")
@RequiredArgsConstructor
@Tag(
		name = "Teacher AI",
		description = "AI-assisted teacher tools for generating quizzes and tests. Requires X-School-Id header."
)
public class TeacherAiController {

	private final TeacherAiQuizService teacherAiQuizService;

	@PostMapping("/{teacherId}/ai/quiz-generator")
	@Operation(
			summary = "Generate quiz/test with AI",
			description = "Creates a reviewable quiz/test draft from class-section, subject, syllabus, difficulty, and question settings."
	)
	public ApiResponse<AiQuizGenerationResponse> generateQuiz(
			@Parameter(description = "Teacher UUID", required = true)
			@PathVariable UUID teacherId,
			@Valid @RequestBody AiQuizGenerationRequest request) {
		return ApiResponse.success(teacherAiQuizService.generate(teacherId, request), "Quiz generated");
	}

}
