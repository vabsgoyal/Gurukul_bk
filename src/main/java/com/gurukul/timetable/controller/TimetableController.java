package com.gurukul.timetable.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.timetable.dto.TimetableDtos.PeriodScheduleRequest;
import com.gurukul.timetable.dto.TimetableDtos.PeriodScheduleResponse;
import com.gurukul.timetable.dto.TimetableDtos.SectionTimetableRequest;
import com.gurukul.timetable.dto.TimetableDtos.TimetableResponse;
import com.gurukul.timetable.service.TimetableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Timetable", description = "Bell schedule and weekly class timetables. Requires X-School-Id and Authorization headers.")
public class TimetableController {

	private final TimetableService timetableService;

	@GetMapping("/api/v1/periods")
	@Operation(summary = "Get this school's bell schedule (periods, breaks, Saturday switch)")
	public ApiResponse<PeriodScheduleResponse> getPeriods() {
		return ApiResponse.success(timetableService.getSchedule());
	}

	@PutMapping("/api/v1/periods")
	@Operation(summary = "Replace this school's bell schedule",
			description = "Admin only. 409 TIMETABLE_IN_USE if a removed period, a new break, or switching Saturday "
					+ "off would orphan saved timetable slots.")
	public ApiResponse<PeriodScheduleResponse> replacePeriods(@Valid @RequestBody PeriodScheduleRequest request) {
		return ApiResponse.success(timetableService.replaceSchedule(request), "Bell schedule saved");
	}

	@GetMapping("/api/v1/class-sections/{sectionId}/timetable")
	@Operation(summary = "Get a class-section's weekly timetable",
			description = "Admin: any section. Teacher: a section they class-teach or teach a subject in. "
					+ "Student: own section. Parent: a linked child's section.")
	public ApiResponse<TimetableResponse> getSectionTimetable(@PathVariable UUID sectionId) {
		return ApiResponse.success(timetableService.getSectionTimetable(sectionId));
	}

	@PutMapping("/api/v1/class-sections/{sectionId}/timetable")
	@Operation(summary = "Replace a class-section's whole weekly timetable",
			description = "Admin only. Each slot's subject+teacher must be assigned to the section. "
					+ "409 TIMETABLE_CLASH (data = clashing slots) if a teacher is already teaching another "
					+ "section at that time; nothing is saved.")
	public ApiResponse<TimetableResponse> replaceSectionTimetable(
			@PathVariable UUID sectionId, @Valid @RequestBody SectionTimetableRequest request) {
		return ApiResponse.success(timetableService.replaceSectionTimetable(sectionId, request), "Timetable saved");
	}

	@GetMapping("/api/v1/timetable/me")
	@Operation(summary = "My timetable",
			description = "Teacher/admin: own periods across sections. Student: own section. "
					+ "Parent: a child's section (childId required when more than one child is linked).")
	public ApiResponse<TimetableResponse> myTimetable(@RequestParam(required = false) UUID childId) {
		return ApiResponse.success(timetableService.getMyTimetable(childId));
	}

}
