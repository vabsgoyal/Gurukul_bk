package com.gurukul.fees.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.fees.dto.FeeAssessmentResponse;
import com.gurukul.fees.dto.FeeStructureRequest;
import com.gurukul.fees.dto.FeeStructureResponse;
import com.gurukul.fees.service.FeeStructureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fee-structures")
@RequiredArgsConstructor
@Tag(name = "Fee Structures", description = "Fee structures by class-section. Requires X-School-Id header.")
public class FeeStructureController {

	private final FeeStructureService feeStructureService;

	@GetMapping
	@Operation(summary = "List fee structures")
	public ApiResponse<List<FeeStructureResponse>> list() {
		return ApiResponse.success(feeStructureService.list());
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get fee structure by ID")
	public ApiResponse<FeeStructureResponse> getById(@PathVariable UUID id) {
		return ApiResponse.success(feeStructureService.getById(id));
	}

	@PostMapping
	@Operation(summary = "Create fee structure")
	public ApiResponse<FeeStructureResponse> create(@Valid @RequestBody FeeStructureRequest request) {
		return ApiResponse.success(feeStructureService.create(request), "Fee structure created");
	}

	@PostMapping("/{id}/generate-assessments")
	@Operation(summary = "Generate fee assessments for all active students in the structure's class-section")
	public ApiResponse<List<FeeAssessmentResponse>> generateAssessments(@PathVariable UUID id) {
		return ApiResponse.success(feeStructureService.generateAssessments(id), "Assessments generated");
	}

}
