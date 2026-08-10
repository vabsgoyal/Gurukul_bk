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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the report-card authorization rework: a class teacher has full marks-entry access
 * across every subject in their own section (not just their own subject), may publish that
 * section's report cards, and neither power extends to a section they don't teach.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClassTeacherReportCardAuthorizationIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void classTeacherCanEnterMarksForAnySubjectInTheirSectionAndPublish() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);

		MvcResult sectionResult = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 7", "section": "CT-%s", "academicYear": "2026-27"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		String sectionId = JsonPath.read(sectionResult.getResponse().getContentAsString(), "$.data.id");

		String subjectTeacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "CT Subject Teacher " + suffix);
		String classTeacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "CT Class Teacher " + suffix);
		String classTeacherBearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "employees", classTeacherId, "TEACHER");

		mockMvc.perform(patch("/api/v1/class-sections/" + sectionId + "/class-teacher")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"teacherId": "%s"}
								""".formatted(classTeacherId)))
				.andExpect(status().isOk());

		MvcResult subjectResult = mockMvc.perform(post("/api/v1/subjects")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code": "CT-SUB-%s", "name": "Class Teacher Test Subject"}
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

		String studentId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, "CT Student " + suffix);

		MvcResult assessmentResult = mockMvc.perform(post("/api/v1/class-sections/" + sectionId + "/assessments")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "title": "Unit Test",
								  "type": "TEST",
								  "subjectId": "%s",
								  "assessmentDate": "2026-09-01",
								  "maxMarks": 50,
								  "teacherId": "%s",
								  "term": "Term 1"
								}
								""".formatted(subjectId, subjectTeacherId)))
				.andExpect(status().isOk())
				.andReturn();
		String assessmentId = JsonPath.read(assessmentResult.getResponse().getContentAsString(), "$.data.id");

		// The class teacher is neither the assessment's creator nor the assigned subject teacher,
		// but can still enter marks for it - full edit access across their section's subjects.
		mockMvc.perform(post("/api/v1/assessments/" + assessmentId + "/results")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + classTeacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"results": [{"studentId": "%s", "marksObtained": 45, "absent": false}]}
								""".formatted(studentId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.results[0].marksObtained").value(45.00));

		// The class teacher can also publish this section's report cards, not just admin.
		mockMvc.perform(post("/api/v1/class-sections/" + sectionId + "/report-cards/publish")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + classTeacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"term": "Term 1"}
								"""))
				.andExpect(status().isOk());
	}

	@Test
	void teacherWithNoRelationToTheSectionCannotEnterMarksOrPublish() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);

		MvcResult sectionResult = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 8", "section": "NR-%s", "academicYear": "2026-27"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		String sectionId = JsonPath.read(sectionResult.getResponse().getContentAsString(), "$.data.id");

		String subjectTeacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "NR Subject Teacher " + suffix);
		String unrelatedTeacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "NR Unrelated Teacher " + suffix);
		String unrelatedTeacherBearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "employees", unrelatedTeacherId, "TEACHER");

		MvcResult subjectResult = mockMvc.perform(post("/api/v1/subjects")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code": "NR-SUB-%s", "name": "Unrelated Test Subject"}
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

		String studentId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, "NR Student " + suffix);

		MvcResult assessmentResult = mockMvc.perform(post("/api/v1/class-sections/" + sectionId + "/assessments")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "title": "Unit Test",
								  "type": "TEST",
								  "subjectId": "%s",
								  "assessmentDate": "2026-09-01",
								  "maxMarks": 50,
								  "teacherId": "%s",
								  "term": "Term 1"
								}
								""".formatted(subjectId, subjectTeacherId)))
				.andExpect(status().isOk())
				.andReturn();
		String assessmentId = JsonPath.read(assessmentResult.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(post("/api/v1/assessments/" + assessmentId + "/results")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + unrelatedTeacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"results": [{"studentId": "%s", "marksObtained": 40, "absent": false}]}
								""".formatted(studentId)))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/class-sections/" + sectionId + "/report-cards/publish")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + unrelatedTeacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"term": "Term 1"}
								"""))
				.andExpect(status().isForbidden());
	}

}
