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

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TeacherManagementIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final String CLASS_SECTION_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void listTeachersAndDashboardReturnSeededData() throws Exception {
		mockMvc.perform(get("/api/v1/teachers").header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(2)))
				.andExpect(jsonPath("$.data[?(@.employeeCode == 'T-1001')]").exists());

		mockMvc.perform(get("/api/v1/teachers/dashboard").header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalTeachers").value(greaterThanOrEqualTo(2)))
				.andExpect(jsonPath("$.data.classTeacherAssignments").value(greaterThanOrEqualTo(1)))
				.andExpect(jsonPath("$.data.features[?(@.feature == 'AI_LESSON_PLANNER')]").exists());
	}

	@Test
	void createTeacherAndAssignClassSection() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String createTeacher = """
				{
				  "employeeCode": "T-%s",
				  "name": "Kavita Rao",
				  "email": "kavita.%s@gurukul.demo",
				  "phone": "9000000000",
				  "qualification": "M.Sc. Physics, B.Ed.",
				  "specialization": "Physics",
				  "joiningDate": "2025-04-01"
				}
				""".formatted(unique, unique);

		MvcResult teacherResult = mockMvc.perform(post("/api/v1/teachers")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createTeacher))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Teacher created"))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andReturn();

		String teacherId = JsonPath.read(teacherResult.getResponse().getContentAsString(), "$.data.id");

		String assignTeacher = """
				{
				  "classSectionId": "%s",
				  "subjectName": "Physics",
				  "assignmentRole": "SUBJECT_TEACHER"
				}
				""".formatted(CLASS_SECTION_A);

		mockMvc.perform(patch("/api/v1/teachers/" + teacherId + "/assignments")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(assignTeacher))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Teacher assigned"))
				.andExpect(jsonPath("$.data.className").value("Grade 8"))
				.andExpect(jsonPath("$.data.subjectName").value("Physics"));

		mockMvc.perform(get("/api/v1/teachers/" + teacherId)
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.assignmentCount").value(1))
				.andExpect(jsonPath("$.data.assignments[0].classSectionLabel").value("Grade 8 - A (2026-27)"));
	}

	@Test
	void duplicateTeacherEmployeeCodeReturns400() throws Exception {
		String body = """
				{
				  "employeeCode": "T-1001",
				  "name": "Duplicate Teacher",
				  "email": "duplicate.teacher@gurukul.demo",
				  "phone": "9000000001",
				  "qualification": "B.Ed.",
				  "specialization": "Science",
				  "joiningDate": "2025-04-01"
				}
				""";

		mockMvc.perform(post("/api/v1/teachers")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Employee code already exists for this school"));
	}

	@Test
	void teacherIsScopedToSchool() throws Exception {
		mockMvc.perform(get("/api/v1/teachers/cccccccc-cccc-cccc-cccc-cccccccccccc")
						.header("X-School-Id", "22222222-2222-2222-2222-222222222222"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("School not found"));
	}

}
