package com.gurukul.students.controller;

import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthContext;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.ApiResponse;
import com.gurukul.common.PageResponse;
import com.gurukul.registration.dto.RegistrationDtos.StudentInviteResponse;
import com.gurukul.registration.service.StudentInviteService;
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
import org.springframework.security.access.AccessDeniedException;
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
	private final StudentInviteService studentInviteService;

	@GetMapping
	@Operation(
			summary = "List students, paginated",
			description = "Returns one page of students for the current school, ordered by name. "
					+ "Defaults to page 0, size 50."
	)
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "200",
					description = "Students retrieved successfully",
					content = @Content(schema = @Schema(implementation = StudentResponse.class))
			)
	})
	public ApiResponse<List<StudentResponse>> list(
			@Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
			@Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size) {
		PageResponse<StudentResponse> result = studentService.list(page, size);
		return ApiResponse.page(result.getContent(), result.isHasNext(), result.getTotalElements());
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

	@GetMapping("/search")
	@Operation(
			summary = "Search students by name or roll number",
			description = "Fuzzy, typo-tolerant match against name and roll number for the current school."
	)
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Matching students returned"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Blank query")
	})
	public ApiResponse<List<StudentResponse>> search(
			@Parameter(description = "Search term", required = true, example = "Rahul")
			@RequestParam String q) {
		return ApiResponse.success(studentService.search(q));
	}

	@GetMapping("/search-parents")
	@Operation(
			summary = "Search students by parent name, parent contact, or student name",
			description = """
					Used by teachers to look up a parent/guardian. There is no separate parent record —
					this matches against the parentName, parentContact, and name fields on Student.
					Fuzzy, typo-tolerant match.
					"""
	)
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Matching students returned"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Blank query")
	})
	public ApiResponse<List<StudentResponse>> searchParents(
			@Parameter(description = "Search term", required = true, example = "9876543210")
			@RequestParam String q) {
		return ApiResponse.success(studentService.searchByParent(q));
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

	@PostMapping("/{id}/invite")
	@Operation(summary = "Generate a self-registration invite code for this specific student (valid 72h, single use)")
	public ApiResponse<StudentInviteResponse> invite(
			@Parameter(description = "Student UUID", required = true)
			@PathVariable UUID id) {
		AuthPrincipal principal = AuthContext.current();
		if (principal.getRole() != Role.ADMIN) {
			throw new AccessDeniedException("Only an admin can do this");
		}
		return ApiResponse.success(studentInviteService.createInviteForStudent(principal, id));
	}

}
