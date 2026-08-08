package com.gurukul.performance.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.performance.dto.PerformanceDtos.EmployeePerformanceSummaryResponse;
import com.gurukul.performance.dto.PerformanceDtos.StudentPerformanceSummaryResponse;
import com.gurukul.performance.service.PerformanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/performance")
@RequiredArgsConstructor
@Tag(name = "Performance", description = "Aggregated academic/result performance dashboards. Requires X-School-Id header.")
public class PerformanceController {

	private final PerformanceService performanceService;

	@GetMapping("/students/{studentId}/summary")
	@Operation(summary = "Student performance summary: exam scores, attendance, overall blended score")
	public ApiResponse<StudentPerformanceSummaryResponse> studentSummary(@PathVariable UUID studentId) {
		return ApiResponse.success(performanceService.studentSummary(studentId));
	}

	@GetMapping("/employees/{employeeId}/summary")
	@Operation(summary = "Employee (teacher) performance summary: feedback and taught-class results")
	public ApiResponse<EmployeePerformanceSummaryResponse> employeeSummary(@PathVariable UUID employeeId) {
		return ApiResponse.success(performanceService.employeeSummary(employeeId));
	}

}
