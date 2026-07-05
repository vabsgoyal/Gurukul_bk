package com.gurukul.expenses.infrastructure.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.expenses.infrastructure.dto.InfraExpenseDtos;
import com.gurukul.expenses.infrastructure.service.InfraExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Infrastructure Expenses", description = "Infrastructure expense workflow. Requires X-School-Id header.")
public class InfraExpenseController {

	private final InfraExpenseService infraExpenseService;

	@GetMapping("/api/v1/infra-expense-categories")
	@Operation(summary = "List infrastructure expense categories")
	public ApiResponse<List<InfraExpenseDtos.CategoryResponse>> listCategories() {
		return ApiResponse.success(infraExpenseService.listCategories());
	}

	@GetMapping("/api/v1/infra-expense-requests")
	@Operation(summary = "List infrastructure expense requests")
	public ApiResponse<List<InfraExpenseDtos.RequestResponse>> listRequests() {
		return ApiResponse.success(infraExpenseService.listRequests());
	}

	@PostMapping("/api/v1/infra-expense-requests")
	@Operation(summary = "Create infrastructure expense request")
	public ApiResponse<InfraExpenseDtos.RequestResponse> create(@Valid @RequestBody InfraExpenseDtos.RequestCreate request) {
		return ApiResponse.success(infraExpenseService.createRequest(request), "Request created");
	}

	@PostMapping("/api/v1/infra-expense-requests/{id}/submit")
	public ApiResponse<InfraExpenseDtos.RequestResponse> submit(@PathVariable UUID id, @RequestBody(required = false) InfraExpenseDtos.ApprovalActionRequest action) {
		return ApiResponse.success(infraExpenseService.submit(id, action != null ? action : new InfraExpenseDtos.ApprovalActionRequest()), "Submitted");
	}

	@PostMapping("/api/v1/infra-expense-requests/{id}/approve")
	public ApiResponse<InfraExpenseDtos.RequestResponse> approve(@PathVariable UUID id, @RequestBody(required = false) InfraExpenseDtos.ApprovalActionRequest action) {
		return ApiResponse.success(infraExpenseService.approve(id, action != null ? action : new InfraExpenseDtos.ApprovalActionRequest()), "Approved");
	}

	@PostMapping("/api/v1/infra-expense-requests/{id}/reject")
	public ApiResponse<InfraExpenseDtos.RequestResponse> reject(@PathVariable UUID id, @RequestBody(required = false) InfraExpenseDtos.ApprovalActionRequest action) {
		return ApiResponse.success(infraExpenseService.reject(id, action != null ? action : new InfraExpenseDtos.ApprovalActionRequest()), "Rejected");
	}

	@PostMapping("/api/v1/infra-expense-requests/{id}/purchase")
	public ApiResponse<InfraExpenseDtos.RequestResponse> purchase(@PathVariable UUID id, @Valid @RequestBody InfraExpenseDtos.PurchaseRequest request) {
		return ApiResponse.success(infraExpenseService.recordPurchase(id, request), "Purchase recorded");
	}

	@PostMapping("/api/v1/infra-expense-requests/{id}/pay")
	public ApiResponse<InfraExpenseDtos.RequestResponse> pay(@PathVariable UUID id, @Valid @RequestBody InfraExpenseDtos.PayRequest request) {
		return ApiResponse.success(infraExpenseService.payVendor(id, request), "Vendor paid");
	}

}
