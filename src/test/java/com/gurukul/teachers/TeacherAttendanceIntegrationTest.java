package com.gurukul.teachers;

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
class TeacherAttendanceIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final String TEACHER_ID = "cccccccc-cccc-cccc-cccc-cccccccccccc";
	private static final String CLASS_SECTION_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void teacherCanMarkAndViewStudentAttendance() throws Exception {
		String firstStudentId = enrollStudent("ATT-A-");
		String secondStudentId = enrollStudent("ATT-B-");
		String sessionName = "Morning-" + UUID.randomUUID().toString().substring(0, 8);

		String markAttendance = """
				{
				  "classSectionId": "%s",
				  "attendanceDate": "2026-07-12",
				  "sessionName": "%s",
				  "entries": [
				    {
				      "studentId": "%s",
				      "status": "PRESENT",
				      "remarks": "On time"
				    },
				    {
				      "studentId": "%s",
				      "status": "ABSENT",
				      "remarks": "No prior notice"
				    }
				  ]
				}
				""".formatted(CLASS_SECTION_A, sessionName, firstStudentId, secondStudentId);

		mockMvc.perform(post("/api/v1/teachers/" + TEACHER_ID + "/attendance")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(markAttendance))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Attendance marked"))
				.andExpect(jsonPath("$.data.totalMarked").value(2))
				.andExpect(jsonPath("$.data.presentCount").value(1))
				.andExpect(jsonPath("$.data.absentCount").value(1));

		mockMvc.perform(get("/api/v1/teachers/class-sections/" + CLASS_SECTION_A + "/attendance")
						.header("X-School-Id", SCHOOL_ID)
						.param("attendanceDate", "2026-07-12")
						.param("sessionName", sessionName))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.records[?(@.studentId == '" + firstStudentId + "' && @.status == 'PRESENT')]").exists())
				.andExpect(jsonPath("$.data.records[?(@.studentId == '" + secondStudentId + "' && @.status == 'ABSENT')]").exists());

		mockMvc.perform(get("/api/v1/teachers/students/" + secondStudentId + "/attendance")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].status").value("ABSENT"));
	}

	@Test
	void markingSameStudentAgainUpdatesExistingAttendanceSlot() throws Exception {
		String studentId = enrollStudent("ATT-C-");
		String sessionName = "Morning-" + UUID.randomUUID().toString().substring(0, 8);

		String present = attendanceBody(studentId, sessionName, "PRESENT", "First mark");
		mockMvc.perform(post("/api/v1/teachers/" + TEACHER_ID + "/attendance")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(present))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.presentCount").value(1));

		String late = attendanceBody(studentId, sessionName, "LATE", "Updated mark");
		mockMvc.perform(post("/api/v1/teachers/" + TEACHER_ID + "/attendance")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(late))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalMarked").value(1))
				.andExpect(jsonPath("$.data.presentCount").value(0))
				.andExpect(jsonPath("$.data.lateCount").value(1))
				.andExpect(jsonPath("$.data.records[0].remarks").value("Updated mark"));
	}

	@Test
	void teacherDashboardShowsAttendanceMarkingAvailable() throws Exception {
		mockMvc.perform(get("/api/v1/teachers/dashboard").header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.features[?(@.feature == 'ATTENDANCE_MARKING' && @.availableInCurrentSlice == true)]").exists());
	}

	private String enrollStudent(String prefix) throws Exception {
		String rollNumber = prefix + UUID.randomUUID().toString().substring(0, 8);
		String body = """
				{
				  "rollNumber": "%s",
				  "name": "Attendance Student",
				  "dob": "2012-05-15",
				  "gender": "MALE",
				  "address": "123 MG Road",
				  "parentName": "Parent Name",
				  "parentContact": "9876543210",
				  "classSectionId": "%s",
				  "admissionDate": "2026-04-01"
				}
				""".formatted(rollNumber, CLASS_SECTION_A);

		MvcResult result = mockMvc.perform(post("/api/v1/students")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andReturn();

		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
	}

	private String attendanceBody(String studentId, String sessionName, String status, String remarks) {
		return """
				{
				  "classSectionId": "%s",
				  "attendanceDate": "2026-07-12",
				  "sessionName": "%s",
				  "entries": [
				    {
				      "studentId": "%s",
				      "status": "%s",
				      "remarks": "%s"
				    }
				  ]
				}
				""".formatted(CLASS_SECTION_A, sessionName, studentId, status, remarks);
	}

}
