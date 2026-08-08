package com.gurukul.performance.service;

import com.gurukul.attendance.entity.AttendanceRecord;
import com.gurukul.attendance.entity.AttendanceStatus;
import com.gurukul.attendance.repository.AttendanceRecordRepository;
import com.gurukul.common.SchoolContext;
import com.gurukul.employees.dto.EmployeeFeedbackResponse;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.entity.EmployeeFeedback;
import com.gurukul.employees.entity.FeedbackCategory;
import com.gurukul.employees.repository.EmployeeFeedbackRepository;
import com.gurukul.employees.service.EmployeeService;
import com.gurukul.exams.dto.AssessmentResultResponse;
import com.gurukul.exams.entity.AssessmentResult;
import com.gurukul.exams.entity.AssessmentType;
import com.gurukul.exams.repository.AssessmentResultRepository;
import com.gurukul.performance.dto.PerformanceDtos.EmployeePerformanceSummaryResponse;
import com.gurukul.performance.dto.PerformanceDtos.EmployeePerformanceSummaryResponse.CategoryBreakdown;
import com.gurukul.performance.dto.PerformanceDtos.EmployeePerformanceSummaryResponse.SectionResultBreakdown;
import com.gurukul.performance.dto.PerformanceDtos.StudentPerformanceSummaryResponse;
import com.gurukul.performance.dto.PerformanceDtos.StudentPerformanceSummaryResponse.AttendanceSummary;
import com.gurukul.performance.dto.PerformanceDtos.StudentPerformanceSummaryResponse.MonthAttendance;
import com.gurukul.performance.dto.PerformanceDtos.StudentPerformanceSummaryResponse.TypeBreakdown;
import com.gurukul.students.entity.ClassSection;
import com.gurukul.students.entity.Student;
import com.gurukul.students.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PerformanceService {

	private static final BigDecimal EXAM_WEIGHT = new BigDecimal("0.7");
	private static final BigDecimal ATTENDANCE_WEIGHT = new BigDecimal("0.3");
	private static final BigDecimal HALF_DAY_WEIGHT = new BigDecimal("0.5");
	private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

	private final AssessmentResultRepository assessmentResultRepository;
	private final AttendanceRecordRepository attendanceRecordRepository;
	private final EmployeeFeedbackRepository employeeFeedbackRepository;
	private final StudentService studentService;
	private final EmployeeService employeeService;
	private final SchoolContext schoolContext;

	public StudentPerformanceSummaryResponse studentSummary(UUID studentId) {
		UUID schoolId = schoolContext.getSchoolId();
		Student student = studentService.getScopedEntity(studentId);

		List<AssessmentResult> results = assessmentResultRepository
				.findAllBySchoolIdAndStudentIdOrderByAssessmentAssessmentDateDesc(schoolId, studentId);

		BigDecimal examAverage = weightedPercentage(results);

		List<TypeBreakdown> byType = results.stream()
				.collect(Collectors.groupingBy(r -> r.getAssessment().getType(), LinkedHashMap::new, Collectors.toList()))
				.entrySet().stream()
				.map(e -> new TypeBreakdown(e.getKey(), weightedPercentage(e.getValue()), e.getValue().size()))
				.sorted(Comparator.comparing(TypeBreakdown::getType))
				.toList();

		List<AssessmentResultResponse> examHistory = results.stream().map(AssessmentResultResponse::from).toList();

		List<AttendanceRecord> attendanceRecords = attendanceRecordRepository
				.findAllBySchoolIdAndStudentIdOrderByAttendanceDateDesc(schoolId, studentId);

		AttendanceSummary attendanceSummary = attendanceSummary(attendanceRecords);

		List<MonthAttendance> byMonth = attendanceRecords.stream()
				.collect(Collectors.groupingBy(r -> r.getAttendanceDate().format(MONTH_FORMAT), LinkedHashMap::new, Collectors.toList()))
				.entrySet().stream()
				.map(e -> new MonthAttendance(e.getKey(), attendanceSummary(e.getValue()).getAttendancePercentage()))
				.sorted(Comparator.comparing(MonthAttendance::getMonth))
				.toList();

		BigDecimal overall = examAverage.multiply(EXAM_WEIGHT)
				.add(attendanceSummary.getAttendancePercentage().multiply(ATTENDANCE_WEIGHT))
				.setScale(2, RoundingMode.HALF_UP);

		return new StudentPerformanceSummaryResponse(
				student.getId(), student.getName(), student.getRollNumber(),
				overall, examAverage, byType, examHistory, attendanceSummary, byMonth
		);
	}

	public EmployeePerformanceSummaryResponse employeeSummary(UUID employeeId) {
		UUID schoolId = schoolContext.getSchoolId();
		Employee employee = employeeService.getScopedEntity(employeeId);

		List<AssessmentResult> results = assessmentResultRepository
				.findAllBySchoolIdAndAssessmentCreatedByTeacherId(schoolId, employeeId);

		BigDecimal overallResult = weightedPercentage(results);

		List<SectionResultBreakdown> bySection = results.stream()
				.collect(Collectors.groupingBy(r -> r.getAssessment().getSection().getId(), LinkedHashMap::new, Collectors.toList()))
				.values().stream()
				.map(group -> {
					ClassSection section = group.get(0).getAssessment().getSection();
					return new SectionResultBreakdown(
							section.getId(), section.getClassName(), section.getSection(), weightedPercentage(group));
				})
				.sorted(Comparator.comparing(SectionResultBreakdown::getClassName).thenComparing(SectionResultBreakdown::getSection))
				.toList();

		List<EmployeeFeedback> feedbackList = employeeFeedbackRepository
				.findAllBySchoolIdAndEmployeeIdOrderByFeedbackDateDesc(schoolId, employeeId);

		BigDecimal averageRating = feedbackList.isEmpty() ? BigDecimal.ZERO : feedbackList.stream()
				.map(EmployeeFeedback::getRating)
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.divide(BigDecimal.valueOf(feedbackList.size()), 2, RoundingMode.HALF_UP);

		List<CategoryBreakdown> byCategory = feedbackList.stream()
				.collect(Collectors.groupingBy(EmployeeFeedback::getCategory, LinkedHashMap::new, Collectors.toList()))
				.entrySet().stream()
				.map(e -> {
					List<EmployeeFeedback> group = e.getValue();
					BigDecimal avg = group.stream().map(EmployeeFeedback::getRating).reduce(BigDecimal.ZERO, BigDecimal::add)
							.divide(BigDecimal.valueOf(group.size()), 2, RoundingMode.HALF_UP);
					return new CategoryBreakdown(e.getKey(), avg, group.size());
				})
				.sorted(Comparator.comparing((CategoryBreakdown c) -> c.getCategory().name()))
				.toList();

		List<EmployeeFeedbackResponse> feedbackHistory = feedbackList.stream().map(EmployeeFeedbackResponse::from).toList();

		return new EmployeePerformanceSummaryResponse(
				employee.getId(), employee.getName(), overallResult, bySection,
				averageRating, byCategory, feedbackHistory
		);
	}

	private BigDecimal weightedPercentage(List<AssessmentResult> results) {
		if (results.isEmpty()) {
			return BigDecimal.ZERO;
		}
		BigDecimal totalObtained = results.stream().map(AssessmentResult::getMarksObtained).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal totalMax = results.stream().map(r -> r.getAssessment().getMaxMarks()).reduce(BigDecimal.ZERO, BigDecimal::add);
		if (totalMax.compareTo(BigDecimal.ZERO) == 0) {
			return BigDecimal.ZERO;
		}
		return totalObtained.multiply(BigDecimal.valueOf(100)).divide(totalMax, 2, RoundingMode.HALF_UP);
	}

	private AttendanceSummary attendanceSummary(List<AttendanceRecord> records) {
		Map<AttendanceStatus, Long> counts = records.stream()
				.collect(Collectors.groupingBy(AttendanceRecord::getStatus, Collectors.counting()));
		int total = records.size();
		int present = counts.getOrDefault(AttendanceStatus.PRESENT, 0L).intValue();
		int absent = counts.getOrDefault(AttendanceStatus.ABSENT, 0L).intValue();
		int late = counts.getOrDefault(AttendanceStatus.LATE, 0L).intValue();
		int halfDay = counts.getOrDefault(AttendanceStatus.HALF_DAY, 0L).intValue();

		BigDecimal percentage = BigDecimal.ZERO;
		if (total > 0) {
			BigDecimal effectivePresent = BigDecimal.valueOf(present)
					.add(BigDecimal.valueOf(late))
					.add(HALF_DAY_WEIGHT.multiply(BigDecimal.valueOf(halfDay)));
			percentage = effectivePresent.multiply(BigDecimal.valueOf(100))
					.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
		}

		return new AttendanceSummary(total, present, absent, late, halfDay, percentage);
	}

}
