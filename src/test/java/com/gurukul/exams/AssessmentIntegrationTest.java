package com.gurukul.exams;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AssessmentIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final String CLASS_SECTION_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void createListUpdateAndDeleteAssessment() throws Exception {
		String adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);

		MvcResult createResult = mockMvc.perform(post("/api/v1/class-sections/" + CLASS_SECTION_A + "/assessments")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"title": "Unit Test 1", "type": "TEST", "assessmentDate": "2026-08-10", "maxMarks": 50}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.title").value("Unit Test 1"))
				.andExpect(jsonPath("$.data.type").value("TEST"))
				.andExpect(jsonPath("$.data.sectionId").value(CLASS_SECTION_A))
				.andReturn();
		String assessmentId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(get("/api/v1/class-sections/" + CLASS_SECTION_A + "/assessments")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.id == '" + assessmentId + "')]").exists());

		mockMvc.perform(get("/api/v1/class-sections/" + CLASS_SECTION_A + "/assessments")
						.header("X-School-Id", SCHOOL_ID)
						.param("type", "QUIZ"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.id == '" + assessmentId + "')]").doesNotExist());

		mockMvc.perform(get("/api/v1/assessments/" + assessmentId)
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.maxMarks").value(50.00));

		mockMvc.perform(put("/api/v1/assessments/" + assessmentId)
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"title": "Unit Test 1 (Revised)", "type": "TEST", "assessmentDate": "2026-08-12", "maxMarks": 60}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.title").value("Unit Test 1 (Revised)"))
				.andExpect(jsonPath("$.data.maxMarks").value(60.00));

		mockMvc.perform(delete("/api/v1/assessments/" + assessmentId)
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/assessments/" + assessmentId)
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isNotFound());
	}

}
