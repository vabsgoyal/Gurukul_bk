package com.gurukul.exams.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.exams.dto.ReportCardDtos.PublicationResponse;
import com.gurukul.exams.dto.ReportCardDtos.PublishRequest;
import com.gurukul.exams.dto.ReportCardDtos.ReportCardResponse;
import com.gurukul.exams.service.ReportCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Report Cards", description = "Term report cards (subject-wise grades + attendance %). Requires X-School-Id header.")
public class ReportCardController {

	private final ReportCardService reportCardService;

	@PostMapping("/api/v1/class-sections/{sectionId}/report-cards/publish")
	@Operation(summary = "Publish (or re-publish) this section's report cards for a term",
			description = "Admin only. Locks marks entry for every assessment in this section/term and makes the report card visible to students.")
	public ApiResponse<PublicationResponse> publish(
			@PathVariable UUID sectionId, @Valid @RequestBody PublishRequest request) {
		return ApiResponse.success(reportCardService.publish(sectionId, request.getTerm()), "Report cards published");
	}

	@GetMapping("/api/v1/students/{studentId}/report-card")
	@Operation(summary = "Get a student's report card for a term",
			description = "A STUDENT session only ever sees their own, and only once published. TEACHER/ADMIN may preview any time.")
	public ApiResponse<ReportCardResponse> get(@PathVariable UUID studentId, @RequestParam String term) {
		return ApiResponse.success(reportCardService.getReportCard(studentId, term));
	}

	@GetMapping("/api/v1/class-sections/{sectionId}/report-cards")
	@Operation(summary = "Get every student's report card for a term, section-wide",
			description = "Admin, or that section's class teacher only. Powers a tabular marks-overview grid instead of opening each student's report card one at a time.")
	public ApiResponse<List<ReportCardResponse>> getForSection(@PathVariable UUID sectionId, @RequestParam String term) {
		return ApiResponse.success(reportCardService.getSectionReportCards(sectionId, term));
	}

}
