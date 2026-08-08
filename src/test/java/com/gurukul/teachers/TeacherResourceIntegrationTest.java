package com.gurukul.teachers;

import com.gurukul.auth.AuthTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TeacherResourceIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final String TEACHER_ID = "cccccccc-cccc-cccc-cccc-cccccccccccc";
	private static final String CLASS_SECTION_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void seededResourcesAndSchedulesAreVisible() throws Exception {
		String bearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);

		mockMvc.perform(get("/api/v1/teachers/" + TEACHER_ID + "/resources")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)))
				.andExpect(jsonPath("$.data[?(@.title == 'Algebra revision notes')]").exists());

		mockMvc.perform(get("/api/v1/teachers/" + TEACHER_ID + "/assessment-schedules")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)))
				.andExpect(jsonPath("$.data[?(@.title == 'Algebra basics quiz')]").exists());
	}

	@Test
	void createResourceAndListByClassSection() throws Exception {
		String bearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String body = """
				{
				  "classSectionId": "%s",
				  "subjectName": "Mathematics",
				  "resourceType": "WORKSHEET",
				  "title": "Fractions practice worksheet",
				  "description": "Practice worksheet for fractions and decimals.",
				  "resourceUrl": "https://resources.gurukul.demo/math/fractions.pdf",
				  "availableOffline": true
				}
				""".formatted(CLASS_SECTION_A);

		mockMvc.perform(post("/api/v1/teachers/" + TEACHER_ID + "/resources")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Teacher resource created"))
				.andExpect(jsonPath("$.data.title").value("Fractions practice worksheet"))
				.andExpect(jsonPath("$.data.availableOffline").value(true));

		mockMvc.perform(get("/api/v1/teachers/class-sections/" + CLASS_SECTION_A + "/resources")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.title == 'Fractions practice worksheet')]").exists());
	}

	@Test
	void uploadResourceFailsGracefullyWhenStorageNotConfigured() throws Exception {
		// No app.storage.s3-bucket is set in local/CI config, so the upload endpoint is reachable
		// and binds the multipart form correctly, but storage itself reports as unconfigured via
		// IllegalStateException - GlobalExceptionHandler maps that to a clean 400, not a 500, since
		// this is a client-correctable "not set up yet" condition, not a server fault.
		String bearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		MockMultipartFile file = new MockMultipartFile("file", "notes.pdf", "application/pdf", "test-content".getBytes());

		mockMvc.perform(multipart("/api/v1/teachers/" + TEACHER_ID + "/resources/upload")
						.file(file)
						.param("classSectionId", CLASS_SECTION_A)
						.param("subjectName", "Mathematics")
						.param("resourceType", "NOTES")
						.param("title", "Uploaded notes")
						.param("description", "Uploaded via multipart test")
						.param("availableOffline", "true")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createAndUpdateAssessmentSchedule() throws Exception {
		String createBody = """
				{
				  "classSectionId": "%s",
				  "subjectName": "Mathematics",
				  "assessmentType": "TEST",
				  "title": "Linear equations test",
				  "scheduledAt": "2026-09-10T10:30:00",
				  "syllabus": "Linear equations in one variable and word problems.",
				  "instructions": "40 minute test. Show all steps.",
				  "maxMarks": 40
				}
				""".formatted(CLASS_SECTION_A);

		MvcResult result = mockMvc.perform(post("/api/v1/teachers/" + TEACHER_ID + "/assessment-schedules")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Assessment scheduled"))
				.andExpect(jsonPath("$.data.status").value("SCHEDULED"))
				.andReturn();

		String scheduleId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");

		String updateBody = """
				{
				  "classSectionId": "%s",
				  "subjectName": "Mathematics",
				  "assessmentType": "TEST",
				  "title": "Linear equations test - updated",
				  "scheduledAt": "2026-09-11T10:30:00",
				  "syllabus": "Linear equations, graph questions, and word problems.",
				  "instructions": "45 minute test. Show all steps.",
				  "maxMarks": 50,
				  "status": "SCHEDULED"
				}
				""".formatted(CLASS_SECTION_A);

		mockMvc.perform(patch("/api/v1/teachers/assessment-schedules/" + scheduleId)
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(updateBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Assessment schedule updated"))
				.andExpect(jsonPath("$.data.title").value("Linear equations test - updated"))
				.andExpect(jsonPath("$.data.maxMarks").value(50));
	}

	@Test
	void teacherDashboardIncludesResourceAndSchedulerFeatures() throws Exception {
		mockMvc.perform(get("/api/v1/teachers/dashboard").header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.features[?(@.feature == 'RESOURCE_LIBRARY' && @.availableInCurrentSlice == true)]").exists())
				.andExpect(jsonPath("$.data.features[?(@.feature == 'QUIZ_TEST_SCHEDULER' && @.availableInCurrentSlice == true)]").exists());
	}

}
