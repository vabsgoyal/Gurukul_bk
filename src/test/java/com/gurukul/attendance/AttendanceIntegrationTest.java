package com.gurukul.attendance;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
						.contentType(MediaType.APPLICATION_JSON)
						.content(markPayload))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.entries[?(@.studentId == '" + studentId + "')].status").value("PRESENT"));

		mockMvc.perform(get("/api/v1/class-sections/" + CLASS_SECTION_B + "/attendance")
						.header("X-School-Id", SCHOOL_ID)
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
						.contentType(MediaType.APPLICATION_JSON)
						.content(remarkPayload))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.entries[?(@.studentId == '" + studentId + "')].status").value("LATE"));

		mockMvc.perform(get("/api/v1/students/" + studentId + "/attendance")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalRecords").value(1))
				.andExpect(jsonPath("$.data.lateCount").value(1))
				.andExpect(jsonPath("$.data.presentCount").value(0));
	}

}
