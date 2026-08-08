package com.gurukul.employees.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.employees.dto.EmployeeFeedbackRequest;
import com.gurukul.employees.dto.EmployeeFeedbackResponse;
import com.gurukul.employees.dto.EmployeeRequest;
import com.gurukul.employees.dto.EmployeeResponse;
import com.gurukul.employees.service.EmployeeFeedbackService;
import com.gurukul.employees.service.EmployeeService;
import com.gurukul.students.dto.ClassSectionResponse;
import com.gurukul.students.service.ClassSectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
@Tag(name = "Employees", description = "Employee master data. Requires X-School-Id header.")
public class EmployeeController {

	private final EmployeeService employeeService;
	private final ClassSectionService classSectionService;
	private final EmployeeFeedbackService employeeFeedbackService;

	@GetMapping
	@Operation(summary = "List employees")
	public ApiResponse<List<EmployeeResponse>> list() {
		return ApiResponse.success(employeeService.list());
	}

	@GetMapping("/search")
	@Operation(summary = "Search employees by name", description = "Fuzzy, typo-tolerant match against name for the current school.")
	public ApiResponse<List<EmployeeResponse>> search(@RequestParam String q) {
		return ApiResponse.success(employeeService.search(q));
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

	@GetMapping("/{id}/class-sections")
	@Operation(summary = "List class-sections where this employee is the class teacher")
	public ApiResponse<List<ClassSectionResponse>> listClassSections(@PathVariable UUID id) {
		return ApiResponse.success(classSectionService.listByClassTeacherId(id));
	}

	@GetMapping("/{id}/feedback")
	@Operation(summary = "List feedback recorded for this employee")
	public ApiResponse<List<EmployeeFeedbackResponse>> listFeedback(@PathVariable UUID id) {
		return ApiResponse.success(employeeFeedbackService.list(id));
	}

	@PostMapping("/{id}/feedback")
	@Operation(summary = "Record feedback for this employee")
	public ApiResponse<EmployeeFeedbackResponse> createFeedback(
			@PathVariable UUID id, @Valid @RequestBody EmployeeFeedbackRequest request) {
		return ApiResponse.success(employeeFeedbackService.create(id, request), "Feedback recorded");
	}

	@PutMapping("/{id}/feedback/{feedbackId}")
	@Operation(summary = "Update a feedback entry")
	public ApiResponse<EmployeeFeedbackResponse> updateFeedback(
			@PathVariable UUID id, @PathVariable UUID feedbackId, @Valid @RequestBody EmployeeFeedbackRequest request) {
		return ApiResponse.success(employeeFeedbackService.update(id, feedbackId, request), "Feedback updated");
	}

	@DeleteMapping("/{id}/feedback/{feedbackId}")
	@Operation(summary = "Delete a feedback entry")
	public ApiResponse<Void> deleteFeedback(@PathVariable UUID id, @PathVariable UUID feedbackId) {
		employeeFeedbackService.delete(id, feedbackId);
		return ApiResponse.success(null, "Feedback deleted");
	}

}
