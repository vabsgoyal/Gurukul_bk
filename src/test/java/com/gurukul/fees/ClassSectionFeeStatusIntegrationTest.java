package com.gurukul.fees;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A class teacher's view of their own section's fee dues (TASK.md Task 11.1). */
@SpringBootTest
@AutoConfigureMockMvc
class ClassSectionFeeStatusIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void classTeacherSeesOwnSectionFeesButAnUnrelatedTeacherCannot() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);

		MvcResult sectionResult = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 9", "section": "CFS-%s", "academicYear": "2026-27"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		String sectionId = JsonPath.read(sectionResult.getResponse().getContentAsString(), "$.data.id");

		String classTeacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "CFS Class Teacher " + suffix);
		String classTeacherBearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "employees", classTeacherId, "TEACHER");
		String unrelatedTeacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "CFS Unrelated Teacher " + suffix);
		String unrelatedTeacherBearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "employees", unrelatedTeacherId, "TEACHER");

		mockMvc.perform(patch("/api/v1/class-sections/" + sectionId + "/class-teacher")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"teacherId": "%s"}
								""".formatted(classTeacherId)))
				.andExpect(status().isOk());

		MvcResult categoryResult = mockMvc.perform(post("/api/v1/fee-categories")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code": "CFS-TUITION-%s", "name": "CFS Tuition"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		String categoryId = JsonPath.read(categoryResult.getResponse().getContentAsString(), "$.data.id");

		MvcResult structureResult = mockMvc.perform(post("/api/v1/fee-structures")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "classSectionId": "%s",
								  "academicYear": "2026-27",
								  "lines": [{"feeCategoryId": "%s", "amount": 5000.00}]
								}
								""".formatted(sectionId, categoryId)))
				.andExpect(status().isOk())
				.andReturn();
		String structureId = JsonPath.read(structureResult.getResponse().getContentAsString(), "$.data.id");

		String studentId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, "CFS Student " + suffix);

		mockMvc.perform(post("/api/v1/fee-structures/" + structureId + "/generate-assessments")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/class-sections/" + sectionId + "/fee-status")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + classTeacherBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.studentId == '" + studentId + "')].totalDue").value(5000.00));

		mockMvc.perform(get("/api/v1/class-sections/" + sectionId + "/fee-status")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + unrelatedTeacherBearer))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/class-sections/" + sectionId + "/fee-status")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.studentId == '" + studentId + "')].totalDue").value(5000.00));
	}

}
