package com.gurukul.expenses.events.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.expenses.events.dto.EventExpenseDtos;
import com.gurukul.expenses.events.service.EventExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events/{eventId}")
@RequiredArgsConstructor
@Tag(name = "Event Expenses", description = "Event budget and expense outflows. Requires X-School-Id header.")
public class EventExpenseController {

	private final EventExpenseService eventExpenseService;

	@PostMapping("/budget")
	@Operation(summary = "Create event budget")
	public ApiResponse<EventExpenseDtos.BudgetResponse> createBudget(
			@PathVariable UUID eventId, @Valid @RequestBody EventExpenseDtos.BudgetRequest request) {
		return ApiResponse.success(eventExpenseService.createBudget(eventId, request), "Budget created");
	}

	@GetMapping("/budget")
	@Operation(summary = "Get event budget")
	public ApiResponse<EventExpenseDtos.BudgetResponse> getBudget(@PathVariable UUID eventId) {
		return ApiResponse.success(eventExpenseService.getBudget(eventId));
	}

	@PostMapping("/expense-requests")
	@Operation(summary = "Create event expense request")
	public ApiResponse<EventExpenseDtos.ExpenseRequestResponse> createExpenseRequest(
			@PathVariable UUID eventId, @Valid @RequestBody EventExpenseDtos.ExpenseRequestCreate request) {
		return ApiResponse.success(eventExpenseService.createExpenseRequest(eventId, request), "Expense request created");
	}

	@PostMapping("/expense-requests/{reqId}/submit")
	public ApiResponse<EventExpenseDtos.ExpenseRequestResponse> submit(
			@PathVariable UUID eventId, @PathVariable UUID reqId,
			@RequestBody(required = false) EventExpenseDtos.ApprovalActionRequest action) {
		return ApiResponse.success(eventExpenseService.submit(eventId, reqId, action != null ? action : new EventExpenseDtos.ApprovalActionRequest()), "Submitted");
	}

	@PostMapping("/expense-requests/{reqId}/approve")
	public ApiResponse<EventExpenseDtos.ExpenseRequestResponse> approve(
			@PathVariable UUID eventId, @PathVariable UUID reqId,
			@RequestBody(required = false) EventExpenseDtos.ApprovalActionRequest action) {
		return ApiResponse.success(eventExpenseService.approve(eventId, reqId, action != null ? action : new EventExpenseDtos.ApprovalActionRequest()), "Approved");
	}

	@PostMapping("/expense-requests/{reqId}/pay")
	public ApiResponse<EventExpenseDtos.ExpenseRequestResponse> pay(
			@PathVariable UUID eventId, @PathVariable UUID reqId, @Valid @RequestBody EventExpenseDtos.PayRequest request) {
		return ApiResponse.success(eventExpenseService.pay(eventId, reqId, request), "Paid");
	}

	@GetMapping("/pnl")
	@Operation(summary = "Event P&L: collections vs expenses and budget vs actual")
	public ApiResponse<EventExpenseDtos.EventPnlResponse> getPnl(@PathVariable UUID eventId) {
		return ApiResponse.success(eventExpenseService.getPnl(eventId));
	}

}
