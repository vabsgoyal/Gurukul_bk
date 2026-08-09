package com.gurukul.employees.controller;

import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthContext;
import com.gurukul.common.ApiResponse;
import com.gurukul.employees.dto.EmployeeRequest;
import com.gurukul.employees.dto.EmployeeResponse;
import com.gurukul.employees.service.EmployeeService;
import com.gurukul.registration.dto.RegistrationDtos.TeacherInviteResponse;
import com.gurukul.registration.service.TeacherInviteService;
import com.gurukul.students.dto.ClassSectionResponse;
import com.gurukul.students.service.ClassSectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
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
	private final TeacherInviteService teacherInviteService;

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

	@PostMapping("/{id}/invite")
	@Operation(summary = "Generate a self-registration invite code for this specific employee (valid 72h, single use)")
	public ApiResponse<TeacherInviteResponse> invite(@PathVariable UUID id) {
		requireAdmin();
		return ApiResponse.success(teacherInviteService.createInviteForEmployee(AuthContext.current(), id));
	}

	@GetMapping("/{id}/invite")
	@Operation(summary = "View the most recently generated invite for this employee",
			description = "Use this to re-share a code the admin lost or navigated away from, or to check "
					+ "whether it's already been used/expired. 404 if no invite has ever been generated for this employee.")
	public ApiResponse<TeacherInviteResponse> getInvite(@PathVariable UUID id) {
		requireAdmin();
		return ApiResponse.success(teacherInviteService.getInviteForEmployee(id));
	}

	private void requireAdmin() {
		if (AuthContext.current().getRole() != Role.ADMIN) {
			throw new AccessDeniedException("Only an admin can do this");
		}
	}

}
