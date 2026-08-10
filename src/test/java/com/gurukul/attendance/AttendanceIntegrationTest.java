package com.gurukul.attendance;

import com.gurukul.auth.AuthTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AttendanceIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final String CLASS_SECTION_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void markSectionAttendanceThenReadRosterAndStudentHistory() throws Exception {
		String rollNumber = "ATT-" + UUID.randomUUID().toString().substring(0, 8);
		MvcResult studentResult = mockMvc.perform(post("/api/v1/students")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "rollNumber": "%s",
								  "name": "Attendance Test Student",
								  "dob": "2012-05-15",
								  "gender": "MALE",
								  "address": "123 MG Road",
								  "parentName": "Parent Name",
								  "parentContact": "9876543210",
								  "classSectionId": "%s",
								  "admissionDate": "2026-04-01"
								}
								""".formatted(rollNumber, CLASS_SECTION_B)))
				.andExpect(status().isOk())
				.andReturn();
		String studentId = JsonPath.read(studentResult.getResponse().getContentAsString(), "$.data.id");

		MvcResult teacherResult = mockMvc.perform(post("/api/v1/employees")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Attendance Teacher", "designation": "Teacher", "joinDate": "2024-04-01"}
								"""))
				.andExpect(status().isOk())
				.andReturn();
		String teacherId = JsonPath.read(teacherResult.getResponse().getContentAsString(), "$.data.id");

		String adminToken = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String bearer = "Bearer " + adminToken;

		String markPayload = """
				{
				  "date": "2026-08-01",
				  "teacherId": "%s",
				  "records": [
				    {"studentId": "%s", "status": "PRESENT"}
				  ]
				}
				""".formatted(teacherId, studentId);

		mockMvc.perform(post("/api/v1/class-sections/" + CLASS_SECTION_B + "/attendance")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(markPayload))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.entries[?(@.studentId == '" + studentId + "')].status").value("PRESENT"));

		mockMvc.perform(get("/api/v1/class-sections/" + CLASS_SECTION_B + "/attendance")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, bearer)
						.param("date", "2026-08-01"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.entries[?(@.studentId == '" + studentId + "')].status").value("PRESENT"));

		String remarkPayload = """
				{
				  "date": "2026-08-01",
				  "teacherId": "%s",
				  "records": [
				    {"studentId": "%s", "status": "LATE", "remarks": "Bus delay"}
				  ]
				}
				""".formatted(teacherId, studentId);

		mockMvc.perform(post("/api/v1/class-sections/" + CLASS_SECTION_B + "/attendance")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(remarkPayload))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.entries[?(@.studentId == '" + studentId + "')].status").value("LATE"));

		mockMvc.perform(get("/api/v1/students/" + studentId + "/attendance")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalRecords").value(1))
				.andExpect(jsonPath("$.data.lateCount").value(1))
				.andExpect(jsonPath("$.data.presentCount").value(0));
	}

	/**
	 * The existing test above only exercises the ADMIN-marks-on-behalf-of-a-teacher path. The
	 * frontend's attendance screen defaults non-admin callers to marking as themselves - this
	 * covers that path directly (a TEACHER who actually is the section's class teacher).
	 */
	@Test
	void classTeacherMarksTheirOwnSectionAttendance() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);

		MvcResult sectionResult = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 10", "section": "ATT-%s", "academicYear": "2026-27"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		String sectionId = JsonPath.read(sectionResult.getResponse().getContentAsString(), "$.data.id");

		String teacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Own-Section Teacher " + suffix);
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
						"/api/v1/class-sections/" + sectionId + "/class-teacher")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"teacherId\": \"" + teacherId + "\"}"))
				.andExpect(status().isOk());
		String teacherBearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "employees", teacherId, "TEACHER");

		String studentId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, "Own-Section Student " + suffix);

		String markPayload = """
				{
				  "date": "2026-08-03",
				  "teacherId": "%s",
				  "records": [
				    {"studentId": "%s", "status": "PRESENT"}
				  ]
				}
				""".formatted(teacherId, studentId);

		mockMvc.perform(post("/api/v1/class-sections/" + sectionId + "/attendance")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(markPayload))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.entries[?(@.studentId == '" + studentId + "')].status").value("PRESENT"));
	}

	@Test
	void sectionHistoryAggregatesEveryStudentsAttendanceOverARange() throws Exception {
		String adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String teacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "History Teacher");

		String studentAId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, CLASS_SECTION_B, "History Student A");
		String studentBId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, CLASS_SECTION_B, "History Student B");

		markDay(adminBearer, teacherId, "2026-08-10", studentAId, "PRESENT", studentBId, "ABSENT");
		markDay(adminBearer, teacherId, "2026-08-11", studentAId, "PRESENT", studentBId, "PRESENT");

		mockMvc.perform(get("/api/v1/class-sections/" + CLASS_SECTION_B + "/attendance/history")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.param("from", "2026-08-10")
						.param("to", "2026-08-11"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.students[?(@.studentId == '" + studentAId + "')].totalRecords").value(2))
				.andExpect(jsonPath("$.data.students[?(@.studentId == '" + studentAId + "')].presentCount").value(2))
				.andExpect(jsonPath("$.data.students[?(@.studentId == '" + studentBId + "')].totalRecords").value(2))
				.andExpect(jsonPath("$.data.students[?(@.studentId == '" + studentBId + "')].presentCount").value(1))
				.andExpect(jsonPath("$.data.students[?(@.studentId == '" + studentBId + "')].absentCount").value(1));
	}

	/**
	 * The test above only covers the from/to branch. This covers the other branch of the same
	 * service method - omitting the date range, which falls back to fetching the section's entire
	 * attendance history (see AttendanceRecordRepository.findStudentStatusesBySchoolIdAndSectionId).
	 */
	@Test
	void sectionHistoryAggregatesEveryStudentsAttendanceWithoutDateRange() throws Exception {
		String adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String teacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "History Teacher No Range");

		String studentAId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, CLASS_SECTION_B, "History Student NoRange A");
		String studentBId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, CLASS_SECTION_B, "History Student NoRange B");

		markDay(adminBearer, teacherId, "2026-08-05", studentAId, "PRESENT", studentBId, "ABSENT");
		markDay(adminBearer, teacherId, "2026-08-06", studentAId, "LATE", studentBId, "HALF_DAY");

		mockMvc.perform(get("/api/v1/class-sections/" + CLASS_SECTION_B + "/attendance/history")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.students[?(@.studentId == '" + studentAId + "')].totalRecords").value(2))
				.andExpect(jsonPath("$.data.students[?(@.studentId == '" + studentAId + "')].presentCount").value(1))
				.andExpect(jsonPath("$.data.students[?(@.studentId == '" + studentAId + "')].lateCount").value(1))
				.andExpect(jsonPath("$.data.students[?(@.studentId == '" + studentBId + "')].totalRecords").value(2))
				.andExpect(jsonPath("$.data.students[?(@.studentId == '" + studentBId + "')].absentCount").value(1))
				.andExpect(jsonPath("$.data.students[?(@.studentId == '" + studentBId + "')].halfDayCount").value(1));
	}

	private void markDay(String adminBearer, String teacherId, String date,
			String studentAId, String statusA, String studentBId, String statusB) throws Exception {
		String payload = """
				{
				  "date": "%s",
				  "teacherId": "%s",
				  "records": [
				    {"studentId": "%s", "status": "%s"},
				    {"studentId": "%s", "status": "%s"}
				  ]
				}
				""".formatted(date, teacherId, studentAId, statusA, studentBId, statusB);

		mockMvc.perform(post("/api/v1/class-sections/" + CLASS_SECTION_B + "/attendance")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(payload))
				.andExpect(status().isOk());
	}

}
