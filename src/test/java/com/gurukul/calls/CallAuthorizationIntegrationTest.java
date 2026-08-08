package com.gurukul.calls;

import com.gurukul.auth.AuthTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A student could previously only call their exact class teacher, not any other subject teacher
 * assigned to their section - this covers the fix letting a student reach a subject teacher too
 * (CallAuthorizationService.isSubjectTeacherOf), on top of the already-covered class-teacher and
 * same-section-classmate cases.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CallAuthorizationIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void studentCanCallASubjectTeacherWhoIsNotTheirClassTeacher() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);

		MvcResult sectionResult = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 9", "section": "CALL-%s", "academicYear": "2026-27"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		String sectionId = JsonPath.read(sectionResult.getResponse().getContentAsString(), "$.data.id");

		String subjectTeacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Subject Teacher " + suffix);

		MvcResult subjectResult = mockMvc.perform(post("/api/v1/subjects")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code": "SUB-%s", "name": "Test Subject"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		String subjectId = JsonPath.read(subjectResult.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(post("/api/v1/class-sections/" + sectionId + "/subjects")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"subjectId": "%s", "teacherId": "%s"}
								""".formatted(subjectId, subjectTeacherId)))
				.andExpect(status().isOk());

		String studentId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, "Call Student " + suffix);
		String studentBearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "students", studentId, "STUDENT");

		mockMvc.perform(post("/api/v1/calls/immediate")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + studentBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"calleeOwnerType": "EMPLOYEE", "calleeOwnerId": "%s"}
								""".formatted(subjectTeacherId)))
				.andExpect(status().isOk());
	}

}
