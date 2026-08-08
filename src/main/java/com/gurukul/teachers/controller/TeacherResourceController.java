package com.gurukul.teachers.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.teachers.dto.TeacherAssessmentScheduleRequest;
import com.gurukul.teachers.dto.TeacherAssessmentScheduleResponse;
import com.gurukul.teachers.dto.TeacherResourceRequest;
import com.gurukul.teachers.dto.TeacherResourceResponse;
import com.gurukul.teachers.dto.TeacherResourceUploadRequest;
import com.gurukul.teachers.service.TeacherResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teachers")
@RequiredArgsConstructor
@Tag(
		name = "Teacher Resources",
		description = "Teacher resource library and quiz/test scheduler. Requires X-School-Id header."
)
public class TeacherResourceController {

	private final TeacherResourceService teacherResourceService;

	@PostMapping("/{teacherId}/resources")
	@Operation(
			summary = "Create teacher resource",
			description = "Adds a book, notes, worksheet, link, or other material for a class-section."
	)
	public ApiResponse<TeacherResourceResponse> createResource(
			@Parameter(description = "Teacher UUID", required = true)
			@PathVariable UUID teacherId,
			@Valid @RequestBody TeacherResourceRequest request) {
		return ApiResponse.success(teacherResourceService.createResource(teacherId, request), "Teacher resource created");
	}

	@PostMapping(value = "/{teacherId}/resources/upload", consumes = "multipart/form-data")
	@Operation(
			summary = "Upload teacher resource file",
			description = "Uploads a book, notes, worksheet, or other file for a class-section and stores it in object storage."
	)
	public ApiResponse<TeacherResourceResponse> uploadResource(
			@Parameter(description = "Teacher UUID", required = true)
			@PathVariable UUID teacherId,
			@Valid @ModelAttribute TeacherResourceUploadRequest request,
			@RequestPart("file") MultipartFile file) {
		return ApiResponse.success(teacherResourceService.uploadResource(teacherId, request, file), "Teacher resource uploaded");
	}

	@GetMapping("/{teacherId}/resources")
	@Operation(summary = "List teacher resources", description = "Returns resources uploaded by a teacher.")
	public ApiResponse<List<TeacherResourceResponse>> listResourcesByTeacher(
			@Parameter(description = "Teacher UUID", required = true)
			@PathVariable UUID teacherId) {
		return ApiResponse.success(teacherResourceService.listResourcesByTeacher(teacherId));
	}

	@GetMapping("/class-sections/{classSectionId}/resources")
	@Operation(
			summary = "List resources by class-section",
			description = "Returns all teacher resources shared for a class-section."
	)
	public ApiResponse<List<TeacherResourceResponse>> listResourcesByClassSection(
			@Parameter(description = "Class-section UUID", required = true)
			@PathVariable UUID classSectionId) {
		return ApiResponse.success(teacherResourceService.listResourcesByClassSection(classSectionId));
	}

	@DeleteMapping("/resources/{resourceId}")
	@Operation(summary = "Delete teacher resource", description = "Deletes a teacher resource from the current school.")
	public ApiResponse<Void> deleteResource(
			@Parameter(description = "Resource UUID", required = true)
			@PathVariable UUID resourceId) {
		teacherResourceService.deleteResource(resourceId);
		return ApiResponse.success(null, "Teacher resource deleted");
	}

	@PostMapping("/{teacherId}/assessment-schedules")
	@Operation(
			summary = "Create quiz/test schedule",
			description = "Schedules an upcoming quiz, test, exam, or assignment check with syllabus and instructions."
	)
	public ApiResponse<TeacherAssessmentScheduleResponse> createSchedule(
			@Parameter(description = "Teacher UUID", required = true)
			@PathVariable UUID teacherId,
			@Valid @RequestBody TeacherAssessmentScheduleRequest request) {
		return ApiResponse.success(teacherResourceService.createSchedule(teacherId, request), "Assessment scheduled");
	}

	@GetMapping("/{teacherId}/assessment-schedules")
	@Operation(summary = "List teacher quiz/test schedules", description = "Returns schedules created by a teacher.")
	public ApiResponse<List<TeacherAssessmentScheduleResponse>> listSchedulesByTeacher(
			@Parameter(description = "Teacher UUID", required = true)
			@PathVariable UUID teacherId) {
		return ApiResponse.success(teacherResourceService.listSchedulesByTeacher(teacherId));
	}

	@GetMapping("/class-sections/{classSectionId}/assessment-schedules")
	@Operation(
			summary = "List schedules by class-section",
			description = "Returns all upcoming or historical quiz/test schedules for a class-section."
	)
	public ApiResponse<List<TeacherAssessmentScheduleResponse>> listSchedulesByClassSection(
			@Parameter(description = "Class-section UUID", required = true)
			@PathVariable UUID classSectionId) {
		return ApiResponse.success(teacherResourceService.listSchedulesByClassSection(classSectionId));
	}

	@PutMapping("/assessment-schedules/{scheduleId}")
	@Operation(summary = "Update quiz/test schedule", description = "Updates schedule date, syllabus, instructions, marks, or status.")
	public ApiResponse<TeacherAssessmentScheduleResponse> updateSchedule(
			@Parameter(description = "Assessment schedule UUID", required = true)
			@PathVariable UUID scheduleId,
			@Valid @RequestBody TeacherAssessmentScheduleRequest request) {
		return ApiResponse.success(teacherResourceService.updateSchedule(scheduleId, request), "Assessment schedule updated");
	}

	@PatchMapping("/assessment-schedules/{scheduleId}")
	@Operation(summary = "Patch quiz/test schedule", description = "Updates schedule date, syllabus, instructions, marks, or status.")
	public ApiResponse<TeacherAssessmentScheduleResponse> patchSchedule(
			@Parameter(description = "Assessment schedule UUID", required = true)
			@PathVariable UUID scheduleId,
			@Valid @RequestBody TeacherAssessmentScheduleRequest request) {
		return ApiResponse.success(teacherResourceService.updateSchedule(scheduleId, request), "Assessment schedule updated");
	}

	@DeleteMapping("/assessment-schedules/{scheduleId}")
	@Operation(summary = "Delete quiz/test schedule", description = "Deletes a quiz/test schedule from the current school.")
	public ApiResponse<Void> deleteSchedule(
			@Parameter(description = "Assessment schedule UUID", required = true)
			@PathVariable UUID scheduleId) {
		teacherResourceService.deleteSchedule(scheduleId);
		return ApiResponse.success(null, "Assessment schedule deleted");
	}

}
