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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the full TASK.md Task 6 flow: create an assessment with a term -> a teacher enters
 * results -> a principal previews the unpublished report card -> a student is blocked from
 * viewing it until published -> publishing makes it visible to the student and locks further
 * marks entry for that section/term.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExamResultsAndReportCardIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void fullExamResultsAndReportCardFlow() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);

		MvcResult sectionResult = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 6", "section": "RC-%s", "academicYear": "2026-27"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		String sectionId = JsonPath.read(sectionResult.getResponse().getContentAsString(), "$.data.id");

		String subjectTeacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "RC Teacher " + suffix);
		String teacherBearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "employees", subjectTeacherId, "TEACHER");

		MvcResult subjectResult = mockMvc.perform(post("/api/v1/subjects")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code": "RC-SUB-%s", "name": "Report Card Subject"}
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

		String studentId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, "RC Student " + suffix);
		String studentBearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "students", studentId, "STUDENT");

		MvcResult assessmentResult = mockMvc.perform(post("/api/v1/class-sections/" + sectionId + "/assessments")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "title": "Term 1 Final",
								  "type": "EXAM",
								  "subjectId": "%s",
								  "assessmentDate": "2026-09-01",
								  "maxMarks": 100,
								  "teacherId": "%s",
								  "term": "Term 1"
								}
								""".formatted(subjectId, subjectTeacherId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.term").value("Term 1"))
				.andReturn();
		String assessmentId = JsonPath.read(assessmentResult.getResponse().getContentAsString(), "$.data.id");

		// Teacher (the assigned subject teacher, not the assessment's creator) enters marks.
		mockMvc.perform(post("/api/v1/assessments/" + assessmentId + "/results")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"results": [{"studentId": "%s", "marksObtained": 82, "absent": false}]}
								""".formatted(studentId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.results[0].marksObtained").value(82.00));

		// Admin can preview the report card before it's published.
		mockMvc.perform(get("/api/v1/students/" + studentId + "/report-card")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.param("term", "Term 1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.published").value(false))
				.andExpect(jsonPath("$.data.subjects[0].marksObtained").value(82.00))
				.andExpect(jsonPath("$.data.subjects[0].percentage").value(82.00))
				.andExpect(jsonPath("$.data.overallGrade").value("A"));

		// The student themselves cannot see it yet.
		mockMvc.perform(get("/api/v1/students/" + studentId + "/report-card")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + studentBearer)
						.param("term", "Term 1"))
				.andExpect(status().isBadRequest());

		// Admin publishes.
		mockMvc.perform(post("/api/v1/class-sections/" + sectionId + "/report-cards/publish")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"term": "Term 1"}
								"""))
				.andExpect(status().isOk());

		// Now the student can see their own, published report card.
		mockMvc.perform(get("/api/v1/students/" + studentId + "/report-card")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + studentBearer)
						.param("term", "Term 1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.published").value(true))
				.andExpect(jsonPath("$.data.overallGrade").value("A"));

		// Marks entry for this section/term is now locked.
		mockMvc.perform(post("/api/v1/assessments/" + assessmentId + "/results")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"results": [{"studentId": "%s", "marksObtained": 90, "absent": false}]}
								""".formatted(studentId)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void gradingScaleFallsBackToDefaultBandsWhenNoneConfigured() throws Exception {
		mockMvc.perform(get("/api/v1/grading-scale").header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(6))
				.andExpect(jsonPath("$.data[0].label").value("A+"));
	}

}
