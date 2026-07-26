package com.gurukul.students;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClassTeacherAssignmentIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void assignClassTeacherThenLookUpByEmployee() throws Exception {
		String sectionSuffix = UUID.randomUUID().toString().substring(0, 8);
		MvcResult sectionResult = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 5", "section": "CT-%s", "academicYear": "2026-27"}
								""".formatted(sectionSuffix)))
				.andExpect(status().isOk())
				.andReturn();
		String sectionId = JsonPath.read(sectionResult.getResponse().getContentAsString(), "$.data.id");

		MvcResult teacherResult = mockMvc.perform(post("/api/v1/employees")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Homeroom Teacher", "designation": "Teacher", "joinDate": "2024-04-01", "employeeType": "TEACHING"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.employeeType").value("TEACHING"))
				.andReturn();
		String teacherId = JsonPath.read(teacherResult.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(patch("/api/v1/class-sections/" + sectionId + "/class-teacher")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"teacherId": "%s"}
								""".formatted(teacherId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(sectionId));

		mockMvc.perform(get("/api/v1/employees/" + teacherId + "/class-sections")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.id == '" + sectionId + "')]").exists());
	}

	@Test
	void sameTeacherCannotBeAssignedToTwoSectionsInSameYear() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);

		MvcResult section1 = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 6", "section": "DUP1-%s", "academicYear": "2026-27"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		String sectionId1 = JsonPath.read(section1.getResponse().getContentAsString(), "$.data.id");

		MvcResult section2 = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 6", "section": "DUP2-%s", "academicYear": "2026-27"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		String sectionId2 = JsonPath.read(section2.getResponse().getContentAsString(), "$.data.id");

		MvcResult teacherResult = mockMvc.perform(post("/api/v1/employees")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Busy Teacher", "designation": "Teacher", "joinDate": "2024-04-01"}
								"""))
				.andExpect(status().isOk())
				.andReturn();
		String teacherId = JsonPath.read(teacherResult.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(patch("/api/v1/class-sections/" + sectionId1 + "/class-teacher")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"teacherId\": \"" + teacherId + "\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(patch("/api/v1/class-sections/" + sectionId2 + "/class-teacher")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"teacherId\": \"" + teacherId + "\"}"))
				.andExpect(status().isBadRequest());
	}

}
