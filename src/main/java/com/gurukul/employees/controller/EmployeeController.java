package com.gurukul.employees.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.employees.dto.EmployeeRequest;
import com.gurukul.employees.dto.EmployeeResponse;
import com.gurukul.employees.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
@Tag(name = "Employees", description = "Employee master data. Requires X-School-Id header.")
public class EmployeeController {

	private final EmployeeService employeeService;

	@GetMapping
	@Operation(summary = "List employees")
	public ApiResponse<List<EmployeeResponse>> list() {
		return ApiResponse.success(employeeService.list());
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get employee by ID")
	public ApiResponse<EmployeeResponse> getById(@PathVariable UUID id) {
		return ApiResponse.success(employeeService.getById(id));
	}

	@PostMapping
	@Operation(summary = "Create employee")
	public ApiResponse<EmployeeResponse> create(@Valid @RequestBody EmployeeRequest request) {
		return ApiResponse.success(employeeService.create(request), "Employee created");
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update employee")
	public ApiResponse<EmployeeResponse> update(@PathVariable UUID id, @Valid @RequestBody EmployeeRequest request) {
		return ApiResponse.success(employeeService.update(id, request), "Employee updated");
	}

}
