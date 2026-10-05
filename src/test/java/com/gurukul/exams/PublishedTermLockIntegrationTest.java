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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Once a (section, term) is published, its assessments are locked: they can't be edited, moved
 * out of or into the term, deleted, or have untermed assessments backfilled into it. Also covers
 * report-card totals ignoring results whose marks were never entered.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PublishedTermLockIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	private String adminBearer;
	private String sectionId;
	private String subjectId;

	@Test
	void publishedTermAssessmentsCannotBeEditedMovedDeletedOrBackfilled() throws Exception {
		setUpSection();
		String published = createAssessment("Published Exam", "Term 1");
		String open = createAssessment("Open Exam", "Term 2");
		String untermed = createAssessment("Untermed Quiz", null);
		publish("Term 1");

		// Edit in place.
		mockMvc.perform(put("/api/v1/assessments/" + published).headers(adminHeaders())
						.contentType(MediaType.APPLICATION_JSON).content(body("Renamed", "Term 1")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("can't be edited")));
		// Move out of the published term (would unlock its marks).
		mockMvc.perform(put("/api/v1/assessments/" + published).headers(adminHeaders())
						.contentType(MediaType.APPLICATION_JSON).content(body("Published Exam", "Term 2")))
				.andExpect(status().isBadRequest());
		// Move another assessment into the published term.
		mockMvc.perform(put("/api/v1/assessments/" + open).headers(adminHeaders())
						.contentType(MediaType.APPLICATION_JSON).content(body("Open Exam", "Term 1")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("can't be moved into it")));
		// Delete.
		mockMvc.perform(delete("/api/v1/assessments/" + published).headers(adminHeaders()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("can't be deleted")));
		// Backfill untermed assessments into the published term.
		mockMvc.perform(patch("/api/v1/class-sections/" + sectionId + "/assessments/backfill-term").headers(adminHeaders())
						.contentType(MediaType.APPLICATION_JSON).content("{\"term\": \"Term 1\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("can't be added to it")));

		mockMvc.perform(get("/api/v1/assessments/" + published).headers(adminHeaders()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.title").value("Published Exam"))
				.andExpect(jsonPath("$.data.term").value("Term 1"));
		mockMvc.perform(get("/api/v1/assessments/" + untermed).headers(adminHeaders()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.term").doesNotExist());

		// Unpublished terms remain editable, deletable and backfillable.
		mockMvc.perform(put("/api/v1/assessments/" + open).headers(adminHeaders())
						.contentType(MediaType.APPLICATION_JSON).content(body("Open Exam v2", "Term 2")))
				.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/class-sections/" + sectionId + "/assessments/backfill-term").headers(adminHeaders())
						.contentType(MediaType.APPLICATION_JSON).content("{\"term\": \"Term 2\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.assessmentsUpdated").value(1));
		mockMvc.perform(delete("/api/v1/assessments/" + open).headers(adminHeaders()))
				.andExpect(status().isOk());
	}

	@Test
	void reportCardLeavesOutResultsWithNoMarksEntered() throws Exception {
		setUpSection();
		String studentId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, "Partial Student");
		String entered = createAssessment("Unit Test 1", "Term 1");
		String blank = createAssessment("Unit Test 2", "Term 1");
		String absent = createAssessment("Unit Test 3", "Term 1");

		submit(entered, "{\"studentId\": \"%s\", \"marksObtained\": 80, \"absent\": false}".formatted(studentId));
		submit(blank, "{\"studentId\": \"%s\", \"absent\": false}".formatted(studentId));
		submit(absent, "{\"studentId\": \"%s\", \"absent\": true}".formatted(studentId));

		// 80 out of 200: the blank result is ignored, the absent one counts as 0 of 100.
		mockMvc.perform(get("/api/v1/students/" + studentId + "/report-card").headers(adminHeaders())
						.param("term", "Term 1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.subjects[0].maxMarks").value(200))
				.andExpect(jsonPath("$.data.subjects[0].marksObtained").value(80.00))
				.andExpect(jsonPath("$.data.subjects[0].percentage").value(40.00));
	}

	private void setUpSection() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		MvcResult sectionResult = mockMvc.perform(post("/api/v1/class-sections").headers(adminHeaders())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 7", "section": "LOCK-%s", "academicYear": "2026-27"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		sectionId = JsonPath.read(sectionResult.getResponse().getContentAsString(), "$.data.id");
		MvcResult subjectResult = mockMvc.perform(post("/api/v1/subjects").headers(adminHeaders())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code": "LOCK-%s", "name": "Lock Subject"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		subjectId = JsonPath.read(subjectResult.getResponse().getContentAsString(), "$.data.id");
	}

	private String createAssessment(String title, String term) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/class-sections/" + sectionId + "/assessments").headers(adminHeaders())
						.contentType(MediaType.APPLICATION_JSON).content(body(title, term)))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
	}

	private String body(String title, String term) {
		return """
				{"title": "%s", "type": "EXAM", "subjectId": "%s", "assessmentDate": "2026-09-01", "maxMarks": 100%s}
				""".formatted(title, subjectId, term == null ? "" : ", \"term\": \"" + term + "\"");
	}

	private void submit(String assessmentId, String entry) throws Exception {
		mockMvc.perform(post("/api/v1/assessments/" + assessmentId + "/results").headers(adminHeaders())
						.contentType(MediaType.APPLICATION_JSON).content("{\"results\": [" + entry + "]}"))
				.andExpect(status().isOk());
	}

	private void publish(String term) throws Exception {
		mockMvc.perform(post("/api/v1/class-sections/" + sectionId + "/report-cards/publish").headers(adminHeaders())
						.contentType(MediaType.APPLICATION_JSON).content("{\"term\": \"" + term + "\"}"))
				.andExpect(status().isOk());
	}

	private HttpHeaders adminHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-School-Id", SCHOOL_ID);
		headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer);
		return headers;
	}

}
