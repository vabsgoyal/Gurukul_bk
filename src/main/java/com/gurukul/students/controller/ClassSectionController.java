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
import org.springframework.web.bind.annotation.RequestParam;
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

	@GetMapping("/classes")
	@Operation(
			summary = "List distinct classes",
			description = "Returns the distinct class/grade names for the school, e.g. for a top-level class-picker tile."
	)
	public ApiResponse<List<String>> listClasses() {
		return ApiResponse.success(classSectionService.listClassNames());
	}

	@GetMapping("/by-class")
	@Operation(
			summary = "List sections within a class",
			description = "Returns all class-sections whose className matches the given value, e.g. all sections of Grade 5."
	)
	public ApiResponse<List<ClassSectionResponse>> listByClass(
			@Parameter(description = "Class or grade name", example = "Grade 5", required = true)
			@RequestParam String className) {
		return ApiResponse.success(classSectionService.listByClassName(className));
	}

	@GetMapping("/{id}")
	@Operation(
			summary = "Get class-section by ID",
			description = "Returns a single class-section's details."
	)
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Class-section found"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Class-section not found")
	})
	public ApiResponse<ClassSectionResponse> getById(
			@Parameter(description = "Class-section UUID", required = true)
			@PathVariable UUID id) {
		return ApiResponse.success(classSectionService.getById(id));
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
