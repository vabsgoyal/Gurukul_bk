package com.gurukul.exams.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.exams.dto.AssessmentRequest;
import com.gurukul.exams.dto.AssessmentResponse;
import com.gurukul.exams.dto.AssessmentTermDtos.BackfillTermRequest;
import com.gurukul.exams.dto.AssessmentTermDtos.BackfillTermResponse;
import com.gurukul.exams.dto.AssessmentTermDtos.TermSummaryResponse;
import com.gurukul.exams.entity.AssessmentType;
import com.gurukul.exams.service.AssessmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Assessments", description = "Assignments, quizzes, tests and exams for a class-section. Requires X-School-Id header.")
public class AssessmentController {

	private final AssessmentService assessmentService;

	@GetMapping("/api/v1/class-sections/{sectionId}/assessments")
	@Operation(summary = "List assessments for a class-section, optionally filtered by type")
	public ApiResponse<List<AssessmentResponse>> list(
			@PathVariable UUID sectionId,
			@RequestParam(required = false) AssessmentType type) {
		return ApiResponse.success(assessmentService.list(sectionId, type));
	}

	@GetMapping("/api/v1/assessments/{id}")
	@Operation(summary = "Get assessment by ID")
	public ApiResponse<AssessmentResponse> getById(@PathVariable UUID id) {
		return ApiResponse.success(assessmentService.getById(id));
	}

	@PostMapping("/api/v1/class-sections/{sectionId}/assessments")
	@Operation(summary = "Create assessment for a class-section")
	public ApiResponse<AssessmentResponse> create(
			@PathVariable UUID sectionId, @Valid @RequestBody AssessmentRequest request) {
		return ApiResponse.success(assessmentService.create(sectionId, request), "Assessment created");
	}

	@PutMapping("/api/v1/assessments/{id}")
	@Operation(summary = "Update assessment")
	public ApiResponse<AssessmentResponse> update(
			@PathVariable UUID id, @Valid @RequestBody AssessmentRequest request) {
		return ApiResponse.success(assessmentService.update(id, request), "Assessment updated");
	}

	@DeleteMapping("/api/v1/assessments/{id}")
	@Operation(summary = "Delete assessment")
	public ApiResponse<Void> delete(@PathVariable UUID id) {
		assessmentService.delete(id);
		return ApiResponse.success(null, "Assessment deleted");
	}

	@GetMapping("/api/v1/class-sections/{sectionId}/terms")
	@Operation(summary = "List distinct terms already used by this section's assessments",
			description = "Backs a tap-to-select term picker on the publish/marks-entry screens instead of free-text guessing.")
	public ApiResponse<List<TermSummaryResponse>> listTerms(@PathVariable UUID sectionId) {
		return ApiResponse.success(assessmentService.listTerms(sectionId));
	}

	@PatchMapping("/api/v1/class-sections/{sectionId}/assessments/backfill-term")
	@Operation(summary = "Assign a term to every assessment in this section that currently has none",
			description = "Admin or this section's class teacher only. Repairs assessments created before a term "
					+ "was set, which would otherwise never appear on any published report card.")
	public ApiResponse<BackfillTermResponse> backfillTerm(
			@PathVariable UUID sectionId, @Valid @RequestBody BackfillTermRequest request) {
		return ApiResponse.success(assessmentService.backfillTerm(sectionId, request.getTerm()), "Terms backfilled");
	}

}
