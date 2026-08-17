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

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the actual real-world bug report: a teacher creates an assessment WITHOUT setting the
 * (optional) term field, enters marks normally, and later an admin publishes a report card - the
 * student's report card comes back "published" but with zero subjects, since
 * ReportCardService.getReportCard requires an exact term-string match and a null Assessment.term
 * can never match a non-null publish term. The existing ExamResultsAndReportCardIntegrationTest
 * cannot catch this because it hardcodes "Term 1" identically everywhere.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReportCardTermMatchingIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void untermedAssessmentNeverShowsUpUntilBackfilledThenStudentAutoResolvesLatestPublishedTerm() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);

		MvcResult sectionResult = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 7", "section": "RCT-%s", "academicYear": "2026-27"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		String sectionId = JsonPath.read(sectionResult.getResponse().getContentAsString(), "$.data.id");

		String teacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "RCT Teacher " + suffix);
		String teacherBearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "employees", teacherId, "TEACHER");

		mockMvc.perform(patch("/api/v1/class-sections/" + sectionId + "/class-teacher")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"teacherId": "%s"}
								""".formatted(teacherId)))
				.andExpect(status().isOk());

		MvcResult subjectResult = mockMvc.perform(post("/api/v1/subjects")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code": "RCT-SUB-%s", "name": "Term Matching Subject"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		String subjectId = JsonPath.read(subjectResult.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(post("/api/v1/class-sections/" + sectionId + "/subjects")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"subjectId": "%s", "teacherId": "%s"}
								""".formatted(subjectId, teacherId)))
				.andExpect(status().isOk());

		String studentId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, "RCT Student " + suffix);
		String studentBearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "students", studentId, "STUDENT");

		// The assessment is created WITHOUT a term - exactly like a real teacher who skips the
		// "optional" field.
		MvcResult assessmentResult = mockMvc.perform(post("/api/v1/class-sections/" + sectionId + "/assessments")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "title": "Unit Test",
								  "type": "TEST",
								  "subjectId": "%s",
								  "assessmentDate": "2026-09-01",
								  "maxMarks": 50,
								  "teacherId": "%s"
								}
								""".formatted(subjectId, teacherId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.term").value(nullValue()))
				.andReturn();
		String assessmentId = JsonPath.read(assessmentResult.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(post("/api/v1/assessments/" + assessmentId + "/results")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"results": [{"studentId": "%s", "marksObtained": 40, "absent": false}]}
								""".formatted(studentId)))
				.andExpect(status().isOk());

		// Class teacher publishes under "Term 1" - the natural, correct action.
		mockMvc.perform(post("/api/v1/class-sections/" + sectionId + "/report-cards/publish")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"term": "Term 1"}
								"""))
				.andExpect(status().isOk());

		// Reproduces the bug report exactly: "published" is true, but the assessment's marks are
		// nowhere to be seen because the assessment itself was never tagged with a term.
		mockMvc.perform(get("/api/v1/students/" + studentId + "/report-card")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + studentBearer)
						.param("term", "Term 1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.published").value(true))
				.andExpect(jsonPath("$.data.subjects.length()").value(0));

		// The class teacher discovers and fixes this: lists terms (correctly shows none yet, since
		// the assessment has no term), then backfills every untermed assessment in the section.
		mockMvc.perform(get("/api/v1/class-sections/" + sectionId + "/terms")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(0));

		mockMvc.perform(patch("/api/v1/class-sections/" + sectionId + "/assessments/backfill-term")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"term": "Term 1"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.assessmentsUpdated").value(1));

		// Now the terms list shows it, published.
		mockMvc.perform(get("/api/v1/class-sections/" + sectionId + "/terms")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].term").value("Term 1"))
				.andExpect(jsonPath("$.data[0].published").value(true));

		// And the student's report card - fetched via the auto-resolved latest published term,
		// exactly like the fixed ReportCardScreen does, with no term guessed/typed at all - now
		// actually shows the marks.
		mockMvc.perform(get("/api/v1/students/" + studentId + "/report-card/published-terms")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + studentBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].term").value("Term 1"));

		mockMvc.perform(get("/api/v1/students/" + studentId + "/report-card")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + studentBearer)
						.param("term", "Term 1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.published").value(true))
				.andExpect(jsonPath("$.data.subjects.length()").value(1))
				.andExpect(jsonPath("$.data.subjects[0].marksObtained").value(40.00));
	}

	@Test
	void publishTrimsWhitespaceSoAccidentalPaddingStillMatchesAssessmentTerm() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);

		MvcResult sectionResult = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 8", "section": "RCT2-%s", "academicYear": "2026-27"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		String sectionId = JsonPath.read(sectionResult.getResponse().getContentAsString(), "$.data.id");

		String teacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "RCT2 Teacher " + suffix);

		MvcResult subjectResult = mockMvc.perform(post("/api/v1/subjects")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code": "RCT2-SUB-%s", "name": "Whitespace Subject"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		String subjectId = JsonPath.read(subjectResult.getResponse().getContentAsString(), "$.data.id");

		String studentId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, "RCT2 Student " + suffix);

		mockMvc.perform(post("/api/v1/class-sections/" + sectionId + "/assessments")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "title": "Whitespace Test",
								  "type": "TEST",
								  "subjectId": "%s",
								  "assessmentDate": "2026-09-01",
								  "maxMarks": 20,
								  "teacherId": "%s",
								  "term": "Term 1"
								}
								""".formatted(subjectId, teacherId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.term").value("Term 1"));

		// Admin publishes with accidental leading/trailing whitespace around the term.
		mockMvc.perform(post("/api/v1/class-sections/" + sectionId + "/report-cards/publish")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"term": "  Term 1  "}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.term").value("Term 1"));

		mockMvc.perform(get("/api/v1/students/" + studentId + "/report-card")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.param("term", "Term 1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.published").value(true));
	}

	@Test
	void unrelatedTeacherCannotListOrBackfillSectionTerms() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);

		MvcResult sectionResult = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 9", "section": "RCT3-%s", "academicYear": "2026-27"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		String sectionId = JsonPath.read(sectionResult.getResponse().getContentAsString(), "$.data.id");

		String unrelatedTeacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "RCT3 Unrelated " + suffix);
		String unrelatedTeacherBearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "employees", unrelatedTeacherId, "TEACHER");

		mockMvc.perform(get("/api/v1/class-sections/" + sectionId + "/terms")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + unrelatedTeacherBearer))
				.andExpect(status().isOk());

		mockMvc.perform(patch("/api/v1/class-sections/" + sectionId + "/assessments/backfill-term")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + unrelatedTeacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"term": "Term 1"}
								"""))
				.andExpect(status().isForbidden());
	}

}
