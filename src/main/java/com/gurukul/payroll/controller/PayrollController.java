package com.gurukul.payroll.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.payroll.dto.PayrollDtos;
import com.gurukul.payroll.service.PayrollService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Payroll", description = "Salary and payroll management. Requires X-School-Id header.")
public class PayrollController {

	private final PayrollService payrollService;

	@GetMapping("/api/v1/salary-structures")
	@Operation(summary = "List salary structures")
	public ApiResponse<List<PayrollDtos.SalaryStructureResponse>> listStructures() {
		return ApiResponse.success(payrollService.listSalaryStructures());
	}

	@PostMapping("/api/v1/salary-structures")
	@Operation(summary = "Create salary structure")
	public ApiResponse<PayrollDtos.SalaryStructureResponse> createStructure(@Valid @RequestBody PayrollDtos.SalaryStructureRequest request) {
		return ApiResponse.success(payrollService.createSalaryStructure(request), "Salary structure created");
	}

	@PostMapping("/api/v1/payroll/runs")
	@Operation(summary = "Create payroll run")
	public ApiResponse<PayrollDtos.PayrollRunResponse> createRun(@Valid @RequestBody PayrollDtos.PayrollRunRequest request) {
		return ApiResponse.success(payrollService.createRun(request), "Payroll run created");
	}

	@PostMapping("/api/v1/payroll/runs/{id}/process")
	@Operation(summary = "Process payroll run")
	public ApiResponse<PayrollDtos.PayrollRunResponse> processRun(@PathVariable UUID id) {
		return ApiResponse.success(payrollService.processRun(id), "Payroll processed");
	}

	@PostMapping("/api/v1/payroll/runs/{id}/pay")
	@Operation(summary = "Pay payroll run")
	public ApiResponse<PayrollDtos.PayrollRunResponse> payRun(@PathVariable UUID id, @Valid @RequestBody PayrollDtos.PayRunRequest request) {
		return ApiResponse.success(payrollService.payRun(id, request), "Payroll paid");
	}

	@GetMapping("/api/v1/payroll/runs/{id}/lines")
	@Operation(summary = "List payroll run lines")
	public ApiResponse<List<PayrollDtos.PayrollLineResponse>> listLines(@PathVariable UUID id) {
		return ApiResponse.success(payrollService.listRunLines(id));
	}

	@GetMapping("/api/v1/employees/{id}/salary-history")
	@Operation(summary = "Employee salary history")
	public ApiResponse<List<PayrollDtos.SalaryHistoryResponse>> salaryHistory(@PathVariable UUID id) {
		return ApiResponse.success(payrollService.salaryHistory(id));
	}

	@GetMapping("/api/v1/payroll/lines/{id}/payslip")
	@Operation(summary = "Get payslip for payroll line")
	public ApiResponse<PayrollDtos.PayslipResponse> getPayslip(@PathVariable UUID id) {
		return ApiResponse.success(payrollService.getPayslip(id));
	}

}
