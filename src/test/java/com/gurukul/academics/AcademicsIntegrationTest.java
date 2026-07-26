package com.gurukul.academics;

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
class AcademicsIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final String CLASS_SECTION_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void createSubjectThenAssignToSection() throws Exception {
		String code = "SUB-" + UUID.randomUUID().toString().substring(0, 8);

		MvcResult subjectResult = mockMvc.perform(post("/api/v1/subjects")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code": "%s", "name": "Mathematics", "description": "Core maths"}
								""".formatted(code)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.code").value(code))
				.andReturn();
		String subjectId = JsonPath.read(subjectResult.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(get("/api/v1/subjects/" + subjectId)
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("Mathematics"));

		mockMvc.perform(get("/api/v1/subjects")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.code == '" + code + "')]").exists());

		MvcResult teacherResult = mockMvc.perform(post("/api/v1/employees")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Subject Teacher", "designation": "Teacher", "joinDate": "2024-04-01"}
								"""))
				.andExpect(status().isOk())
				.andReturn();
		String teacherId = JsonPath.read(teacherResult.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(post("/api/v1/class-sections/" + CLASS_SECTION_A + "/subjects")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"subjectId": "%s", "teacherId": "%s"}
								""".formatted(subjectId, teacherId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.subjectId").value(subjectId))
				.andExpect(jsonPath("$.data.teacherId").value(teacherId));

		mockMvc.perform(get("/api/v1/class-sections/" + CLASS_SECTION_A + "/subjects")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.subjectId == '" + subjectId + "')]").exists());

		mockMvc.perform(get("/api/v1/employees/" + teacherId + "/subject-assignments")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.subjectId == '" + subjectId + "')]").exists())
				.andExpect(jsonPath("$.data[?(@.sectionId == '" + CLASS_SECTION_A + "')]").exists());
	}

}
