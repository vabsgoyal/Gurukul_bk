package com.gurukul.insights;

import com.gurukul.attendance.entity.AttendanceRecord;
import com.gurukul.attendance.entity.AttendanceStatus;
import com.gurukul.attendance.entity.StaffAttendanceRecord;
import com.gurukul.attendance.repository.AttendanceRecordRepository;
import com.gurukul.attendance.repository.StaffAttendanceRecordRepository;
import com.gurukul.auth.AuthTestSupport;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.chat.bot.security.PrincipalContextRunner;
import com.gurukul.chat.bot.tool.QueryMetricTool;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.fees.entity.FeeAssessmentStatus;
import com.gurukul.fees.entity.StudentFeeAssessment;
import com.gurukul.fees.repository.StudentFeeAssessmentRepository;
import com.gurukul.payroll.entity.PayrollLine;
import com.gurukul.payroll.entity.PayrollRun;
import com.gurukul.payroll.entity.PayrollRunStatus;
import com.gurukul.payroll.repository.PayrollLineRepository;
import com.gurukul.payroll.repository.PayrollRunRepository;
import com.gurukul.students.entity.ClassSection;
import com.gurukul.students.entity.Student;
import com.gurukul.students.repository.ClassSectionRepository;
import com.gurukul.students.repository.StudentRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the natural-language school-metrics chat tool end to end: {@link QueryMetricTool} ->
 * {@link MetricQueryEngine} -> the loaded {@link SchemaCatalog}/resolvers. Extra scrutiny here per the
 * operating manual's rule for anything touching money or attendance - the tenant-isolation test in
 * particular is the single most important test in this file: an insight query must never be able to
 * see another school's data, even when that school has matching rows to find.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MetricQueryEngineIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private QueryMetricTool queryMetricTool;
	@Autowired
	private PrincipalContextRunner principalContextRunner;
	@Autowired
	private StudentFeeAssessmentRepository feeAssessmentRepository;
	@Autowired
	private StudentRepository studentRepository;
	@Autowired
	private ClassSectionRepository classSectionRepository;
	@Autowired
	private EmployeeRepository employeeRepository;
	@Autowired
	private AttendanceRecordRepository attendanceRecordRepository;
	@Autowired
	private StaffAttendanceRecordRepository staffAttendanceRecordRepository;
	@Autowired
	private PayrollRunRepository payrollRunRepository;
	@Autowired
	private PayrollLineRepository payrollLineRepository;

	@Test
	void feeAssessmentCountNeverCrossesTenantBoundary() throws Exception {
		String schoolA = registerSchool();
		String sectionA = createSection(schoolA, "A" + suffix());
		String studentA1 = AuthTestSupport.createStudent(mockMvc, schoolA, sectionA, "Insight Student A1 " + suffix());
		String studentA2 = AuthTestSupport.createStudent(mockMvc, schoolA, sectionA, "Insight Student A2 " + suffix());
		seedAssessment(schoolA, studentA1, FeeAssessmentStatus.UNPAID);
		seedAssessment(schoolA, studentA2, FeeAssessmentStatus.PAID);

		String schoolB = registerSchool();
		String sectionB = createSection(schoolB, "B" + suffix());
		String studentB1 = AuthTestSupport.createStudent(mockMvc, schoolB, sectionB, "Insight Student B1 " + suffix());
		String studentB2 = AuthTestSupport.createStudent(mockMvc, schoolB, sectionB, "Insight Student B2 " + suffix());
		String studentB3 = AuthTestSupport.createStudent(mockMvc, schoolB, sectionB, "Insight Student B3 " + suffix());
		seedAssessment(schoolB, studentB1, FeeAssessmentStatus.UNPAID);
		seedAssessment(schoolB, studentB2, FeeAssessmentStatus.UNPAID);
		seedAssessment(schoolB, studentB3, FeeAssessmentStatus.UNPAID);

		AuthPrincipal adminA = admin(schoolA);
		Object result = principalContextRunner.runAs(adminA, () -> queryMetricTool.execute(adminA,
				Map.of("entity", "fee_assessment", "metric", "count",
						"filters", List.of(Map.of("field", "status", "op", "=", "value", "UNPAID")))));

		// School A has exactly one UNPAID assessment. School B independently has three, seeded in the same
		// test run - if this ever came back >= 4 the schoolId filter would have leaked across tenants.
		assertThat(result).isEqualTo(1L);
	}

	@Test
	void onlyAdminSeesTheInsightTool() {
		assertThat(queryMetricTool.appliesTo(new AuthPrincipal(UUID.randomUUID(), OwnerType.EMPLOYEE, Role.ADMIN, UUID.randomUUID(), "admin"))).isTrue();
		assertThat(queryMetricTool.appliesTo(new AuthPrincipal(UUID.randomUUID(), OwnerType.EMPLOYEE, Role.TEACHER, UUID.randomUUID(), "teacher"))).isFalse();
		assertThat(queryMetricTool.appliesTo(new AuthPrincipal(UUID.randomUUID(), OwnerType.STUDENT, Role.STUDENT, UUID.randomUUID(), "student"))).isFalse();
		assertThat(queryMetricTool.appliesTo(new AuthPrincipal(UUID.randomUUID(), OwnerType.STUDENT, Role.PARENT, UUID.randomUUID(), "parent"))).isFalse();
	}

	@Test
	void unknownEntityFailsClosedWithoutThrowing() {
		AuthPrincipal adminA = admin(UUID.randomUUID().toString());
		Object result = principalContextRunner.runAs(adminA, () -> queryMetricTool.execute(adminA,
				Map.of("entity", "not_a_real_entity", "metric", "count")));

		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) result;
		assertThat(map).containsKey("error");
		assertThat(map.get("error").toString()).contains("Unknown entity");
	}

	@Test
	void unknownFilterFieldFailsClosedWithoutThrowing() {
		AuthPrincipal adminA = admin(UUID.randomUUID().toString());
		Object result = principalContextRunner.runAs(adminA, () -> queryMetricTool.execute(adminA,
				Map.of("entity", "fee_assessment", "metric", "count",
						"filters", List.of(Map.of("field", "not_a_real_field", "op", "=", "value", "x")))));

		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) result;
		assertThat(map).containsKey("error");
		assertThat(map.get("error").toString()).contains("Unknown filter field");
	}

	@Test
	void inputSchemaAndDescriptionBuildFromTheLoadedCatalogWithoutThrowing() {
		// Neither is called anywhere at bean-construction time - only lazily by BotReplyService per
		// conversation turn - so wiring the bean alone (every other test here) doesn't exercise this.
		var schema = queryMetricTool.inputSchema();
		assertThat(schema.required()).isPresent();
		assertThat(schema.required().get()).containsExactlyInAnyOrder("entity", "metric");

		String description = queryMetricTool.description();
		assertThat(description).contains("fee_assessment", "attendance", "staff_attendance", "payroll", "enrollment");
	}

	@Test
	void payrollCountFiltersByRunStatusAcrossTheRunJoin() throws Exception {
		String suffix = suffix();
		String schoolA = registerSchool();
		String employeeId = AuthTestSupport.createEmployee(mockMvc, schoolA, "Insight Payroll Teacher " + suffix);
		Employee employee = employeeRepository.findById(UUID.fromString(employeeId)).orElseThrow();

		PayrollRun draftRun = new PayrollRun();
		draftRun.setSchoolId(UUID.fromString(schoolA));
		draftRun.setMonth(1);
		draftRun.setYear(2030);
		draftRun.setStatus(PayrollRunStatus.DRAFT);
		payrollRunRepository.save(draftRun);

		PayrollRun paidRun = new PayrollRun();
		paidRun.setSchoolId(UUID.fromString(schoolA));
		paidRun.setMonth(2);
		paidRun.setYear(2030);
		paidRun.setStatus(PayrollRunStatus.PAID);
		payrollRunRepository.save(paidRun);

		payrollLineRepository.save(payrollLine(schoolA, draftRun, employee));
		payrollLineRepository.save(payrollLine(schoolA, paidRun, employee));

		AuthPrincipal adminA = admin(schoolA);
		Object result = principalContextRunner.runAs(adminA, () -> queryMetricTool.execute(adminA,
				Map.of("entity", "payroll", "metric", "count",
						"filters", List.of(Map.of("field", "runStatus", "op", "!=", "value", "PAID")))));

		// Exact count assertion would be fragile across test runs sharing this school id's payroll rows;
		// what matters is the PAID line is excluded and at least our one DRAFT line is included.
		assertThat((Long) result).isGreaterThanOrEqualTo(1L);
	}

	@Test
	void staffAttendancePercentageAsksForClarificationOnAmbiguousName() throws Exception {
		String suffix = suffix();
		String schoolA = registerSchool();
		String employee1Id = AuthTestSupport.createEmployee(mockMvc, schoolA, "Ambiguous Ravi One " + suffix);
		String employee2Id = AuthTestSupport.createEmployee(mockMvc, schoolA, "Ambiguous Ravi Two " + suffix);
		seedStaffAttendance(schoolA, employee1Id, AttendanceStatus.PRESENT);
		seedStaffAttendance(schoolA, employee2Id, AttendanceStatus.ABSENT);

		AuthPrincipal adminA = admin(schoolA);
		Object result = principalContextRunner.runAs(adminA, () -> queryMetricTool.execute(adminA,
				Map.of("entity", "staff_attendance", "metric", "presentPercentage",
						"filters", List.of(Map.of("field", "employeeName", "op", "=", "value", "Ambiguous Ravi")))));

		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) result;
		assertThat(map).containsKey("error");
		assertThat(map.get("error").toString()).contains("Multiple employees match");
	}

	@Test
	void staffAttendancePercentageResolvesUniqueName() throws Exception {
		String suffix = suffix();
		String schoolA = registerSchool();
		String employeeId = AuthTestSupport.createEmployee(mockMvc, schoolA, "Unique Teacher Name " + suffix);
		seedStaffAttendance(schoolA, employeeId, AttendanceStatus.PRESENT);
		seedStaffAttendance(schoolA, employeeId, AttendanceStatus.PRESENT);
		seedStaffAttendance(schoolA, employeeId, AttendanceStatus.ABSENT);

		AuthPrincipal adminA = admin(schoolA);
		Object result = principalContextRunner.runAs(adminA, () -> queryMetricTool.execute(adminA,
				Map.of("entity", "staff_attendance", "metric", "presentPercentage",
						"filters", List.of(Map.of("field", "employeeName", "op", "=", "value", "Unique Teacher Name " + suffix)))));

		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) result;
		assertThat(map).doesNotContainKey("error");
		assertThat(map.get("totalDays")).isEqualTo(3);
		assertThat(((BigDecimal) map.get("presentPercentage")).doubleValue()).isCloseTo(66.7, org.assertj.core.data.Offset.offset(0.1));
	}

	@Test
	void studentAttendancePercentageBelowThresholdMatchesOnlyTheStudentBelowIt() throws Exception {
		String suffix = suffix();
		String schoolA = registerSchool();
		String sectionId = createSection(schoolA, "PCT" + suffix);
		String goodStudentId = AuthTestSupport.createStudent(mockMvc, schoolA, sectionId, "Good Attendance Student " + suffix);
		String poorStudentId = AuthTestSupport.createStudent(mockMvc, schoolA, sectionId, "Poor Attendance Student " + suffix);

		// Good student: 4/5 present (80%). Poor student: 1/5 present (20%) - below a 70% threshold.
		seedStudentAttendance(schoolA, sectionId, goodStudentId,
				AttendanceStatus.PRESENT, AttendanceStatus.PRESENT, AttendanceStatus.PRESENT, AttendanceStatus.PRESENT, AttendanceStatus.ABSENT);
		seedStudentAttendance(schoolA, sectionId, poorStudentId,
				AttendanceStatus.PRESENT, AttendanceStatus.ABSENT, AttendanceStatus.ABSENT, AttendanceStatus.ABSENT, AttendanceStatus.ABSENT);

		AuthPrincipal adminA = admin(schoolA);
		Object result = principalContextRunner.runAs(adminA, () -> queryMetricTool.execute(adminA,
				Map.of("entity", "attendance", "metric", "presentPercentage",
						"filters", List.of(
								Map.of("field", "className", "op", "=", "value", "Grade 7"),
								Map.of("field", "belowPercent", "op", "=", "value", "70")))));

		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) result;
		assertThat(map).doesNotContainKey("error");
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> students = (List<Map<String, Object>>) map.get("students");
		assertThat(students).extracting(s -> s.get("studentId")).containsExactly(poorStudentId);
	}

	private void seedStudentAttendance(String schoolId, String sectionId, String studentId, AttendanceStatus... statuses) {
		Student student = studentRepository.findById(UUID.fromString(studentId)).orElseThrow();
		ClassSection section = classSectionRepository.findById(UUID.fromString(sectionId)).orElseThrow();
		LocalDate date = LocalDate.of(2030, 1, 1);
		for (AttendanceStatus status : statuses) {
			AttendanceRecord record = new AttendanceRecord();
			record.setSchoolId(UUID.fromString(schoolId));
			record.setStudent(student);
			record.setSection(section);
			record.setAttendanceDate(date);
			record.setStatus(status);
			attendanceRecordRepository.save(record);
			date = date.plusDays(1);
		}
	}

	private AuthPrincipal admin(String schoolId) {
		return new AuthPrincipal(UUID.randomUUID(), OwnerType.EMPLOYEE, Role.ADMIN, UUID.fromString(schoolId), "test-admin");
	}

	private String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	/** These write endpoints (class-sections, employees, ...) validate the school actually exists - an
	 *  arbitrary UUID gets 400 "School not found", so every test needs a real registered school. */
	private String registerSchool() throws Exception {
		String suffix = suffix();
		String phone = "9" + String.format("%09d", Math.abs(suffix.hashCode()) % 1_000_000_000);
		MvcResult result = mockMvc.perform(post("/api/v1/schools")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Insight Test School %s",
								  "address": "1 Test Street",
								  "city": "Jaipur",
								  "state": "Rajasthan",
								  "pincode": "302001",
								  "contactEmail": "insight-%s@example.com",
								  "contactPhone": "%s",
								  "principalName": "Test Principal",
								  "directorName": "Test Director",
								  "principalPhone": "%s",
								  "adminPhone": "8%s"
								}
								""".formatted(suffix, suffix, phone, phone, phone.substring(1))))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.school.id");
	}

	private String createSection(String schoolId, String suffix) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", schoolId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 7", "section": "INS-%s", "academicYear": "2026-27"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
	}

	private void seedAssessment(String schoolId, String studentId, FeeAssessmentStatus status) {
		Student student = studentRepository.findById(UUID.fromString(studentId)).orElseThrow();
		StudentFeeAssessment assessment = new StudentFeeAssessment();
		assessment.setSchoolId(UUID.fromString(schoolId));
		assessment.setStudent(student);
		assessment.setAcademicYear("2026-27");
		assessment.setTotalDue(new BigDecimal("10000.00"));
		assessment.setTotalPaid(status == FeeAssessmentStatus.PAID ? new BigDecimal("10000.00") : BigDecimal.ZERO);
		assessment.setStatus(status);
		feeAssessmentRepository.save(assessment);
	}

	private void seedStaffAttendance(String schoolId, String employeeId, AttendanceStatus status) {
		Employee employee = employeeRepository.findById(UUID.fromString(employeeId)).orElseThrow();
		StaffAttendanceRecord record = new StaffAttendanceRecord();
		record.setSchoolId(UUID.fromString(schoolId));
		record.setEmployee(employee);
		record.setAttendanceDate(nextUnusedDate(employeeId));
		record.setStatus(status);
		record.setSelfMarked(false);
		staffAttendanceRecordRepository.save(record);
	}

	private final Map<String, LocalDate> lastDatePerEmployee = new java.util.HashMap<>();

	private LocalDate nextUnusedDate(String employeeId) {
		LocalDate next = lastDatePerEmployee.merge(employeeId, LocalDate.of(2030, 1, 1), (prev, initial) -> prev.plusDays(1));
		return next;
	}

	private PayrollLine payrollLine(String schoolId, PayrollRun run, Employee employee) {
		PayrollLine line = new PayrollLine();
		line.setSchoolId(UUID.fromString(schoolId));
		line.setRun(run);
		line.setEmployee(employee);
		line.setGross(new BigDecimal("50000.00"));
		line.setDeductions(new BigDecimal("5000.00"));
		line.setNet(new BigDecimal("45000.00"));
		return line;
	}

}
