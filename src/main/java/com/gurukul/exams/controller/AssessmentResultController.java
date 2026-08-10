package com.gurukul.exams.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.exams.dto.AssessmentResultDtos.AssessmentResultsResponse;
import com.gurukul.exams.dto.AssessmentResultDtos.SubmitResultsRequest;
import com.gurukul.exams.service.AssessmentResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Assessment Results", description = "Per-student marks for an assessment. Requires X-School-Id header.")
public class AssessmentResultController {

	private final AssessmentResultService assessmentResultService;

	@PostMapping("/api/v1/assessments/{assessmentId}/results")
	@Operation(summary = "Submit/update marks for an assessment",
			description = "Admin, the assessment's own section+subject teacher, its creator, or that section's class teacher (any subject). Rejected once the section's term has been published.")
	public ApiResponse<AssessmentResultsResponse> submit(
			@PathVariable UUID assessmentId, @Valid @RequestBody SubmitResultsRequest request) {
		return ApiResponse.success(assessmentResultService.submitResults(assessmentId, request), "Results saved");
	}

	@GetMapping("/api/v1/assessments/{assessmentId}/results")
	@Operation(summary = "Get every student's result for an assessment (roster with marks, null where not yet entered)")
	public ApiResponse<AssessmentResultsResponse> get(@PathVariable UUID assessmentId) {
		return ApiResponse.success(assessmentResultService.getResultsForAssessment(assessmentId));
	}

}
