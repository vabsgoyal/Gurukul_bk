package com.gurukul.fees.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.common.PageResponse;
import com.gurukul.fees.dto.FeeAssessmentResponse;
import com.gurukul.fees.dto.FeePaymentRequest;
import com.gurukul.fees.dto.FeePaymentRequestResponse;
import com.gurukul.fees.dto.FeePaymentResponse;
import com.gurukul.fees.dto.PaymentAttemptResponse;
import com.gurukul.fees.dto.PaymentAttemptResultRequest;
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
	@Operation(summary = "List fee assessments, paginated, optionally filtered by status and/or class-section",
			description = "Defaults to page 0, size 50.")
	public ApiResponse<List<FeeAssessmentResponse>> listAssessments(
			@RequestParam(required = false) FeeAssessmentStatus status,
			@RequestParam(required = false) UUID classSectionId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		PageResponse<FeeAssessmentResponse> result = feePaymentService.listAssessmentsPage(status, classSectionId, page, size);
		return ApiResponse.page(result.getContent(), result.isHasNext(), result.getTotalElements());
	}

	@GetMapping("/api/v1/students/{studentId}/fee-assessments")
	@Operation(summary = "List fee assessments for a student")
	public ApiResponse<List<FeeAssessmentResponse>> listByStudent(@PathVariable UUID studentId) {
		return ApiResponse.success(feePaymentService.listByStudent(studentId));
	}

	@GetMapping("/api/v1/class-sections/{id}/fee-status")
	@Operation(summary = "List fee assessments for every student in a class-section",
			description = "Admin, or that section's own class teacher only.")
	public ApiResponse<List<FeeAssessmentResponse>> getClassSectionFeeStatus(@PathVariable UUID id) {
		return ApiResponse.success(feePaymentService.getClassSectionFeeStatus(id));
	}

	@PostMapping("/api/v1/fee-payments")
	@Operation(summary = "Record a fee payment (partial payments allowed)")
	public ApiResponse<FeePaymentResponse> recordPayment(@Valid @RequestBody FeePaymentRequest request) {
		return ApiResponse.success(feePaymentService.recordPayment(request), "Payment recorded");
	}

	@PostMapping("/api/v1/fee-assessments/{id}/payment-request")
	@Operation(summary = "Create a UPI payment request for a student's remaining due, "
			+ "returning a deep link that opens a UPI app (e.g. PhonePe) with the amount pre-filled")
	public ApiResponse<FeePaymentRequestResponse> createPaymentRequest(@PathVariable UUID id) {
		return ApiResponse.success(feePaymentService.createPaymentRequest(id));
	}

	@GetMapping("/api/v1/fee-payments/{id}")
	@Operation(summary = "Get fee payment by ID")
	public ApiResponse<FeePaymentResponse> getPayment(@PathVariable UUID id) {
		return ApiResponse.success(feePaymentService.getPayment(id));
	}

	@GetMapping("/api/v1/fee-assessments/{id}/payment-attempts/pending")
	@Operation(summary = "Check for an in-flight (INITIATED/PENDING) payment attempt on this assessment, "
			+ "so the client can warn before starting another one")
	public ApiResponse<PaymentAttemptResponse> findPendingAttempt(@PathVariable UUID id) {
		return ApiResponse.success(feePaymentService.findPendingAttempt(id).orElse(null));
	}

	@GetMapping("/api/v1/fee-assessments/{id}/payment-attempts")
	@Operation(summary = "List all payment attempts for a fee assessment, most recent first")
	public ApiResponse<List<PaymentAttemptResponse>> listAttempts(@PathVariable UUID id) {
		return ApiResponse.success(feePaymentService.listAttemptsForAssessment(id));
	}

	@PostMapping("/api/v1/payment-attempts/{transactionRef}/result")
	@Operation(summary = "Record what the UPI app returned (or the user self-reported) after control "
			+ "returned to this app for a given payment attempt")
	public ApiResponse<PaymentAttemptResponse> recordAttemptResult(
			@PathVariable String transactionRef, @Valid @RequestBody PaymentAttemptResultRequest request) {
		return ApiResponse.success(feePaymentService.recordAttemptResult(transactionRef, request));
	}

}
