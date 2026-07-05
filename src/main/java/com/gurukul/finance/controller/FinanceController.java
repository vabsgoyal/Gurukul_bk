package com.gurukul.finance.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.finance.dto.FinancialTransactionResponse;
import com.gurukul.finance.dto.FundSummaryResponse;
import com.gurukul.finance.dto.ManualTransactionRequest;
import com.gurukul.finance.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/finance")
@RequiredArgsConstructor
@Tag(name = "Finance", description = "Financial ledger and fund summary. Requires X-School-Id header.")
public class FinanceController {

	private final LedgerService ledgerService;

	@GetMapping("/transactions")
	@Operation(summary = "List financial transactions")
	public ApiResponse<List<FinancialTransactionResponse>> listTransactions() {
		return ApiResponse.success(ledgerService.listTransactions());
	}

	@GetMapping("/summary")
	@Operation(summary = "Get school fund balance summary")
	public ApiResponse<FundSummaryResponse> getSummary() {
		return ApiResponse.success(ledgerService.getSummary());
	}

	@PostMapping("/transactions")
	@Operation(summary = "Record a manual inflow or outflow")
	public ApiResponse<FinancialTransactionResponse> recordManual(
			@Valid @RequestBody ManualTransactionRequest request) {
		return ApiResponse.success(ledgerService.recordManual(request), "Transaction recorded");
	}

}
