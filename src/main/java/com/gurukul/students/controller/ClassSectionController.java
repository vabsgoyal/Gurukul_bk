package com.gurukul.students.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.students.dto.ClassSectionRequest;
import com.gurukul.students.dto.ClassSectionResponse;
import com.gurukul.students.dto.StudentResponse;
import com.gurukul.students.service.ClassSectionService;
import com.gurukul.students.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/class-sections")
@RequiredArgsConstructor
@Tag(
		name = "Class Sections",
		description = "Grade + section combinations for enrollment. Requires X-School-Id header."
)
public class ClassSectionController {

	private final ClassSectionService classSectionService;
	private final StudentService studentService;

	@GetMapping
	@Operation(
			summary = "List class-sections",
			description = "Returns all class-sections for the school in the X-School-Id header. Use for enrollment dropdowns."
	)
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Class-sections retrieved"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Missing or invalid X-School-Id header")
	})
	public ApiResponse<List<ClassSectionResponse>> list() {
		return ApiResponse.success(classSectionService.list());
	}

	@PostMapping
	@Operation(
			summary = "Create class-section",
			description = "Creates a grade + section + academic year combination for the current school."
	)
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Class-section created"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed or duplicate class-section")
	})
	public ApiResponse<ClassSectionResponse> create(@Valid @RequestBody ClassSectionRequest request) {
		return ApiResponse.success(classSectionService.create(request), "Class-section created");
	}

	@GetMapping("/{classSectionId}/students")
	@Operation(
			summary = "List students in a class-section",
			description = "Returns all students enrolled in the given class-section UUID."
	)
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Students retrieved"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Class-section not found")
	})
	public ApiResponse<List<StudentResponse>> listStudents(
			@Parameter(description = "Class-section UUID", required = true)
			@PathVariable UUID classSectionId) {
		return ApiResponse.success(studentService.listByClassSectionId(classSectionId));
	}

}
