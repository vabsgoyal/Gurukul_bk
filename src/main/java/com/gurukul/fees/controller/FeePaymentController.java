package com.gurukul.fees.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.fees.dto.FeeAssessmentResponse;
import com.gurukul.fees.dto.FeePaymentRequest;
import com.gurukul.fees.dto.FeePaymentResponse;
import com.gurukul.fees.entity.FeeAssessmentStatus;
import com.gurukul.fees.service.FeePaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Fee Payments", description = "Student fee assessments and payments. Requires X-School-Id header.")
public class FeePaymentController {

	private final FeePaymentService feePaymentService;

	@GetMapping("/api/v1/fee-assessments")
	@Operation(summary = "List fee assessments, optionally filtered by status")
	public ApiResponse<List<FeeAssessmentResponse>> listAssessments(
			@RequestParam(required = false) FeeAssessmentStatus status) {
		return ApiResponse.success(feePaymentService.listAssessments(status));
	}

	@GetMapping("/api/v1/students/{studentId}/fee-assessments")
	@Operation(summary = "List fee assessments for a student")
	public ApiResponse<List<FeeAssessmentResponse>> listByStudent(@PathVariable UUID studentId) {
		return ApiResponse.success(feePaymentService.listByStudent(studentId));
	}

	@PostMapping("/api/v1/fee-payments")
	@Operation(summary = "Record a fee payment (partial payments allowed)")
	public ApiResponse<FeePaymentResponse> recordPayment(@Valid @RequestBody FeePaymentRequest request) {
		return ApiResponse.success(feePaymentService.recordPayment(request), "Payment recorded");
	}

	@GetMapping("/api/v1/fee-payments/{id}")
	@Operation(summary = "Get fee payment by ID")
	public ApiResponse<FeePaymentResponse> getPayment(@PathVariable UUID id) {
		return ApiResponse.success(feePaymentService.getPayment(id));
	}

}
