package com.gurukul.students.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.students.dto.StudentClassSectionUpdateRequest;
import com.gurukul.students.dto.StudentRequest;
import com.gurukul.students.dto.StudentResponse;
import com.gurukul.students.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
@Tag(
		name = "Students",
		description = "One-time student enrollment and lifecycle management. Requires X-School-Id header."
)
public class StudentController {

	private final StudentService studentService;

	@GetMapping
	@Operation(
			summary = "List students",
			description = "Returns every student for the current school, ordered by creation time."
	)
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "200",
					description = "Students retrieved successfully",
					content = @Content(schema = @Schema(implementation = StudentResponse.class))
			)
	})
	public ApiResponse<List<StudentResponse>> list() {
		return ApiResponse.success(studentService.list());
	}

	@GetMapping("/by-class-section")
	@Operation(
			summary = "List students by class and section",
			description = """
					Returns all students in a specific grade + section + academic year for the current school.
					All three query parameters are required.
					"""
	)
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Students retrieved"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Class-section not found or missing query params")
	})
	public ApiResponse<List<StudentResponse>> listByClassSection(
			@Parameter(description = "Class or grade name", example = "Grade 8", required = true)
			@RequestParam String className,
			@Parameter(description = "Section", example = "A", required = true)
			@RequestParam String section,
			@Parameter(description = "Academic year", example = "2026-27", required = true)
			@RequestParam String academicYear) {
		return ApiResponse.success(studentService.listByClassSection(className, section, academicYear));
	}

	@GetMapping("/{id}")
	@Operation(
			summary = "Get student by ID",
			description = "Returns a single student when the ID exists in the current school scope."
	)
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "200",
					description = "Student found"
			),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "404",
					description = "Student not found"
			)
	})
	public ApiResponse<StudentResponse> getById(
			@Parameter(description = "Student UUID", required = true, example = "ce109a0f-55b3-4db5-b04a-3ed3154b8772")
			@PathVariable UUID id) {
		return ApiResponse.success(studentService.getById(id));
	}

	@PostMapping
	@Operation(
			summary = "Enroll student",
			description = """
					One-time enrollment intake with all essential fields.
					Registers a new student with status ACTIVE.
					Roll number must be unique within the school.
					classSectionId must reference a class-section in the same school.
					"""
	)
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "200",
					description = "Student created successfully"
			),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "400",
					description = "Validation failed or roll number already exists"
			)
	})
	public ApiResponse<StudentResponse> create(
			@Valid @RequestBody StudentRequest request) {
		return ApiResponse.success(studentService.create(request), "Student created");
	}

	@PatchMapping("/{id}/class-section")
	@Operation(
			summary = "Transfer student to another class-section",
			description = """
					Updates only the student's class-section (section transfer / promotion).
					The target classSectionId must belong to the same school.
					"""
	)
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Class-section updated"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid class-section"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Student not found")
	})
	public ApiResponse<StudentResponse> updateClassSection(
			@Parameter(description = "Student UUID", required = true)
			@PathVariable UUID id,
			@Valid @RequestBody StudentClassSectionUpdateRequest request) {
		return ApiResponse.success(studentService.updateClassSection(id, request), "Class-section updated");
	}

	@PutMapping("/{id}")
	@Operation(
			summary = "Update student",
			description = """
					Updates an existing student in the current school scope.
					Optionally set status to ACTIVE, ALUMNI, or WITHDRAWN.
					"""
	)
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "200",
					description = "Student updated successfully"
			),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "400",
					description = "Validation failed or duplicate roll number"
			),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "404",
					description = "Student not found"
			)
	})
	public ApiResponse<StudentResponse> update(
			@Parameter(description = "Student UUID", required = true)
			@PathVariable UUID id,
			@Valid @RequestBody StudentRequest request) {
		return ApiResponse.success(studentService.update(id, request), "Student updated");
	}

	@DeleteMapping("/{id}")
	@Operation(
			summary = "Delete student",
			description = "Permanently removes a student record from the current school scope."
	)
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "200",
					description = "Student deleted successfully"
			),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "404",
					description = "Student not found"
			)
	})
	public ApiResponse<Void> delete(
			@Parameter(description = "Student UUID", required = true)
			@PathVariable UUID id) {
		studentService.delete(id);
		return ApiResponse.success(null, "Student deleted");
	}

}
