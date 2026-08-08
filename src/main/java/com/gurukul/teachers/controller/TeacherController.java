package com.gurukul.teachers.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.teachers.dto.TeacherAssignmentRequest;
import com.gurukul.teachers.dto.TeacherAssignmentResponse;
import com.gurukul.teachers.dto.TeacherDashboardResponse;
import com.gurukul.teachers.dto.TeacherFeatureResponse;
import com.gurukul.teachers.dto.TeacherRequest;
import com.gurukul.teachers.dto.TeacherResponse;
import com.gurukul.teachers.service.TeacherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teachers")
@RequiredArgsConstructor
@Tag(
		name = "Teachers",
		description = "Teacher profiles, class-section assignments, and teacher dashboard features. Requires X-School-Id header."
)
public class TeacherController {

	private final TeacherService teacherService;

	@GetMapping
	@Operation(summary = "List teachers", description = "Returns every teacher for the current school.")
	public ApiResponse<List<TeacherResponse>> list() {
		return ApiResponse.success(teacherService.list());
	}

	@GetMapping("/dashboard")
	@Operation(
			summary = "Teacher dashboard",
			description = "Returns teacher counts, assignment counts, and the teacher feature catalog for the current school."
	)
	public ApiResponse<TeacherDashboardResponse> dashboard() {
		return ApiResponse.success(teacherService.dashboard());
	}

	@GetMapping("/features")
	@Operation(
			summary = "List teacher features",
			description = "Returns teacher-facing product features, including which are available in the current backend slice."
	)
	public ApiResponse<List<TeacherFeatureResponse>> features() {
		return ApiResponse.success(teacherService.features());
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get teacher by ID", description = "Returns one teacher with class-section assignments.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Teacher found"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Teacher not found")
	})
	public ApiResponse<TeacherResponse> getById(
			@Parameter(description = "Teacher UUID", required = true)
			@PathVariable UUID id) {
		return ApiResponse.success(teacherService.getById(id));
	}

	@PostMapping
	@Operation(
			summary = "Create teacher",
			description = "Creates a teacher profile for the current school. Employee code and email must be unique inside the school."
	)
	public ApiResponse<TeacherResponse> create(@Valid @RequestBody TeacherRequest request) {
		return ApiResponse.success(teacherService.create(request), "Teacher created");
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update teacher", description = "Updates teacher profile and optional lifecycle status.")
	public ApiResponse<TeacherResponse> update(
			@Parameter(description = "Teacher UUID", required = true)
			@PathVariable UUID id,
			@Valid @RequestBody TeacherRequest request) {
		return ApiResponse.success(teacherService.update(id, request), "Teacher updated");
	}

	@PatchMapping("/{id}/assignments")
	@Operation(
			summary = "Assign teacher to class-section",
			description = "Assigns a teacher to a class-section, subject, and role such as SUBJECT_TEACHER or CLASS_TEACHER."
	)
	public ApiResponse<TeacherAssignmentResponse> assignClass(
			@Parameter(description = "Teacher UUID", required = true)
			@PathVariable UUID id,
			@Valid @RequestBody TeacherAssignmentRequest request) {
		return ApiResponse.success(teacherService.assignClass(id, request), "Teacher assigned");
	}

	@GetMapping("/{id}/assignments")
	@Operation(summary = "List teacher assignments", description = "Returns all class-section assignments for a teacher.")
	public ApiResponse<List<TeacherAssignmentResponse>> listAssignments(
			@Parameter(description = "Teacher UUID", required = true)
			@PathVariable UUID id) {
		return ApiResponse.success(teacherService.listAssignments(id));
	}

	@GetMapping("/class-sections/{classSectionId}/assignments")
	@Operation(
			summary = "List assignments by class-section",
			description = "Returns all teacher subject assignments for a class-section."
	)
	public ApiResponse<List<TeacherAssignmentResponse>> listAssignmentsByClassSection(
			@Parameter(description = "Class-section UUID", required = true)
			@PathVariable UUID classSectionId) {
		return ApiResponse.success(teacherService.listAssignmentsByClassSection(classSectionId));
	}

	@DeleteMapping("/assignments/{assignmentId}")
	@Operation(summary = "Delete teacher assignment", description = "Removes a teacher's class-section assignment.")
	public ApiResponse<Void> deleteAssignment(
			@Parameter(description = "Teacher assignment UUID", required = true)
			@PathVariable UUID assignmentId) {
		teacherService.deleteAssignment(assignmentId);
		return ApiResponse.success(null, "Teacher assignment deleted");
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete teacher", description = "Permanently removes a teacher profile from the current school.")
	public ApiResponse<Void> delete(
			@Parameter(description = "Teacher UUID", required = true)
			@PathVariable UUID id) {
		teacherService.delete(id);
		return ApiResponse.success(null, "Teacher deleted");
	}

}
