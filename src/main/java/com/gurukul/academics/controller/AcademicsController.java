package com.gurukul.academics.controller;

import com.gurukul.academics.dto.AcademicsDtos.SectionSubjectRequest;
import com.gurukul.academics.dto.AcademicsDtos.SubjectAssignmentResponse;
import com.gurukul.academics.dto.AcademicsDtos.SubjectRequest;
import com.gurukul.academics.dto.AcademicsDtos.SubjectResponse;
import com.gurukul.academics.service.AcademicsService;
import com.gurukul.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Academics", description = "Subjects and section-subject-teacher assignments. Requires X-School-Id header.")
public class AcademicsController {

	private final AcademicsService academicsService;

	@GetMapping("/api/v1/subjects")
	@Operation(summary = "List subjects")
	public ApiResponse<List<SubjectResponse>> listSubjects() {
		return ApiResponse.success(academicsService.listSubjects());
	}

	@GetMapping("/api/v1/subjects/{subjectId}")
	@Operation(summary = "Get subject by ID")
	public ApiResponse<SubjectResponse> getSubject(@PathVariable UUID subjectId) {
		return ApiResponse.success(academicsService.getSubject(subjectId));
	}

	@PostMapping("/api/v1/subjects")
	@Operation(summary = "Create subject")
	public ApiResponse<SubjectResponse> createSubject(@Valid @RequestBody SubjectRequest request) {
		return ApiResponse.success(academicsService.createSubject(request), "Subject created");
	}

	@GetMapping("/api/v1/class-sections/{sectionId}/subjects")
	@Operation(summary = "List subjects assigned to a class-section")
	public ApiResponse<List<SubjectAssignmentResponse>> listSectionSubjects(@PathVariable UUID sectionId) {
		return ApiResponse.success(academicsService.listSectionSubjects(sectionId));
	}

	@PostMapping("/api/v1/class-sections/{sectionId}/subjects")
	@Operation(summary = "Assign a subject and teacher to a class-section")
	public ApiResponse<SubjectAssignmentResponse> assignSubjectToSection(
			@PathVariable UUID sectionId, @Valid @RequestBody SectionSubjectRequest request) {
		return ApiResponse.success(academicsService.assignSubjectToSection(sectionId, request), "Subject assigned");
	}

}
