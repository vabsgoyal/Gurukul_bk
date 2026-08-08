package com.gurukul.teachers.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "Teacher dashboard summary for current school")
public class TeacherDashboardResponse {

	private UUID schoolId;
	private long totalTeachers;
	private long activeTeachers;
	private long classTeacherAssignments;
	private long subjectTeacherAssignments;
	private long assignedClassSections;
	private List<TeacherFeatureResponse> features;

}
