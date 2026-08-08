package com.gurukul.exams.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ReportCardDtos {

	@Getter @Setter
	public static class PublishRequest {
		@NotBlank
		@Schema(description = "Free-text term, matching the term used on the section's assessments", example = "Term 1")
		private String term;
	}

	@Getter @AllArgsConstructor
	public static class PublicationResponse {
		private UUID classSectionId;
		private String term;
		private Instant publishedAt;
		private String publishedByEmployeeName;
	}

	@Getter @AllArgsConstructor
	public static class SubjectResultResponse {
		private UUID subjectId;
		private String subjectName;
		private String subjectCode;
		private BigDecimal maxMarks;
		private BigDecimal marksObtained;
		private BigDecimal percentage;
		private String grade;
	}

	@Getter @AllArgsConstructor
	@Schema(description = "A student's full report card for one term - subjects is empty if no results have been entered yet")
	public static class ReportCardResponse {
		private UUID studentId;
		private String studentName;
		private String rollNumber;
		private String className;
		private String section;
		private String academicYear;
		private String term;
		private List<SubjectResultResponse> subjects;
		private BigDecimal totalMaxMarks;
		private BigDecimal totalMarksObtained;
		private BigDecimal overallPercentage;
		private String overallGrade;
		@Schema(description = "Null if the student has no attendance records at all yet")
		private BigDecimal attendancePercentage;
		private boolean published;
		private Instant publishedAt;
	}

}
