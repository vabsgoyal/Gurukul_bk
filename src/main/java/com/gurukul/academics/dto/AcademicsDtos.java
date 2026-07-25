package com.gurukul.academics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

public class AcademicsDtos {

	@Getter @Setter
	public static class SubjectRequest {
		@NotBlank private String code;
		@NotBlank private String name;
		private String description;
	}

	@Getter @AllArgsConstructor
	public static class SubjectResponse {
		private UUID id;
		private String code;
		private String name;
		private String description;
	}

	@Getter @Setter
	public static class GradeInitializationRequest {
		@NotBlank private String className;
		@NotBlank private String academicYear;
	}

	@Getter @Setter
	public static class SectionSubjectRequest {
		@NotNull private UUID subjectId;
		@NotNull private UUID teacherId;
	}

	@Getter @AllArgsConstructor
	public static class AcademicPlanResponse {
		private UUID sectionId;
		private String className;
		private String section;
		private String academicYear;
		private TeacherShortResponse classTeacher;
		private List<SubjectAssignmentResponse> subjectAssignments;
	}

	@Getter @AllArgsConstructor
	public static class SubjectAssignmentResponse {
		private UUID subjectId;
		private String subjectName;
		private String subjectCode;
		private UUID teacherId;
		private String teacherName;
	}

	@Getter @AllArgsConstructor
	public static class TeacherShortResponse {
		private UUID id;
		private String name;
	}
}
