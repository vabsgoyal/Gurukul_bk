package com.gurukul.performance.dto;

import com.gurukul.employees.entity.FeedbackCategory;
import com.gurukul.exams.dto.AssessmentResultResponse;
import com.gurukul.exams.entity.AssessmentType;
import com.gurukul.employees.dto.EmployeeFeedbackResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class PerformanceDtos {

	@Getter @AllArgsConstructor
	@Schema(description = "Full performance summary for one student")
	public static class StudentPerformanceSummaryResponse {
		private UUID studentId;
		private String studentName;
		private String rollNumber;
		@Schema(description = "Blended score: 70% weighted exam average + 30% attendance percentage")
		private BigDecimal overallPerformancePercentage;
		@Schema(description = "Weighted average across all recorded exam results: sum(marksObtained)/sum(maxMarks) * 100")
		private BigDecimal examWeightedAveragePercentage;
		private List<TypeBreakdown> byAssessmentType;
		private List<AssessmentResultResponse> examHistory;
		private AttendanceSummary attendance;
		private List<MonthAttendance> attendanceByMonth;

		@Getter @AllArgsConstructor
		@Schema(description = "Exam average broken down by assessment type")
		public static class TypeBreakdown {
			private AssessmentType type;
			private BigDecimal averagePercentage;
			private int count;
		}

		@Getter @AllArgsConstructor
		@Schema(description = "Attendance record counts and overall percentage")
		public static class AttendanceSummary {
			private int totalDays;
			private int presentCount;
			private int absentCount;
			private int lateCount;
			private int halfDayCount;
			@Schema(description = "(present + late + 0.5*halfDay) / total * 100")
			private BigDecimal attendancePercentage;
		}

		@Getter @AllArgsConstructor
		@Schema(description = "Attendance percentage for one calendar month")
		public static class MonthAttendance {
			@Schema(example = "2026-07")
			private String month;
			private BigDecimal percentage;
		}
	}

	@Getter @AllArgsConstructor
	@Schema(description = "Full performance summary for one employee (teacher)")
	public static class EmployeePerformanceSummaryResponse {
		private UUID employeeId;
		private String employeeName;
		@Schema(description = "Weighted average % across AssessmentResults for assessments this employee created")
		private BigDecimal overallResultPercentage;
		private List<SectionResultBreakdown> byClassSection;
		private BigDecimal averageFeedbackRating;
		private List<CategoryBreakdown> feedbackByCategory;
		private List<EmployeeFeedbackResponse> feedbackHistory;

		@Getter @AllArgsConstructor
		@Schema(description = "Result average for one class-section this employee teaches/created assessments for")
		public static class SectionResultBreakdown {
			private UUID sectionId;
			private String className;
			private String section;
			private BigDecimal averagePercentage;
		}

		@Getter @AllArgsConstructor
		@Schema(description = "Average feedback rating for one category")
		public static class CategoryBreakdown {
			private FeedbackCategory category;
			private BigDecimal averageRating;
			private int count;
		}
	}

}
