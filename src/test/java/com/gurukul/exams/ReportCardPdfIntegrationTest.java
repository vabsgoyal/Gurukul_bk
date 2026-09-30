package com.gurukul.exams;

import com.gurukul.auth.AuthTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The PDF endpoints must enforce exactly the JSON endpoints' access rules (they share
 * ReportCardService), and SecurityConfig must list the new paths - an unlisted path would fall
 * through to permitAll().
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReportCardPdfIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final String TERM = "Term 1";

	@Autowired
	private MockMvc mockMvc;

	private String adminBearer;
	private String sectionId;
	private String hindiStudentId;
	private String otherStudentId;
	private String hindiStudentBearer;
	private String classTeacherBearer;
	private String unrelatedTeacherBearer;

	@BeforeEach
	void setUp() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);

		MvcResult sectionResult = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 6", "section": "PDF-%s", "academicYear": "2026-27"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		sectionId = JsonPath.read(sectionResult.getResponse().getContentAsString(), "$.data.id");

		String classTeacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "PDF Class Teacher " + suffix);
		classTeacherBearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "employees", classTeacherId, "TEACHER");
		String unrelatedTeacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "PDF Unrelated Teacher " + suffix);
		unrelatedTeacherBearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "employees", unrelatedTeacherId, "TEACHER");
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
								{"code": "PDF-SUB-%s", "name": "Mathematics"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		String subjectId = JsonPath.read(subjectResult.getResponse().getContentAsString(), "$.data.id");
		mockMvc.perform(post("/api/v1/class-sections/" + sectionId + "/subjects")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"subjectId": "%s", "teacherId": "%s"}
								""".formatted(subjectId, classTeacherId)))
				.andExpect(status().isOk());

		hindiStudentId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, "प्रिया शर्मा");
		otherStudentId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, "Aarav PDF " + suffix);
		hindiStudentBearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "students", hindiStudentId, "STUDENT");

		MvcResult assessmentResult = mockMvc.perform(post("/api/v1/class-sections/" + sectionId + "/assessments")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"title": "Unit Test", "type": "TEST", "subjectId": "%s", "assessmentDate": "2026-09-01",
								 "maxMarks": 50, "teacherId": "%s", "term": "%s"}
								""".formatted(subjectId, classTeacherId, TERM)))
				.andExpect(status().isOk())
				.andReturn();
		String assessmentId = JsonPath.read(assessmentResult.getResponse().getContentAsString(), "$.data.id");
		mockMvc.perform(post("/api/v1/assessments/" + assessmentId + "/results")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"results": [{"studentId": "%s", "marksObtained": 45, "absent": false},
								             {"studentId": "%s", "marksObtained": 30, "absent": false}]}
								""".formatted(hindiStudentId, otherStudentId)))
				.andExpect(status().isOk());
	}

	@Test
	void staffPreviewOfAnUnpublishedCardIsWatermarkedDraftAndStudentsAreBlocked() throws Exception {
		byte[] pdf = expectPdf(as(get("/api/v1/students/" + hindiStudentId + "/report-card.pdf"), adminBearer));
		try (PDDocument document = Loader.loadPDF(pdf)) {
			assertThat(document.getNumberOfPages()).isEqualTo(1);
			assertThat(new PDFTextStripper().getText(document)).contains("DRAFT", "Mathematics", "Report Card - Term 1");
		}

		// Not published yet - a student gets the same refusal as the JSON endpoint.
		mockMvc.perform(as(get("/api/v1/students/" + hindiStudentId + "/report-card.pdf"), hindiStudentBearer))
				.andExpect(status().isBadRequest());
	}

	@Test
	void studentGetsOnlyTheirOwnPublishedCard() throws Exception {
		publish();

		byte[] pdf = expectPdf(as(get("/api/v1/students/" + hindiStudentId + "/report-card.pdf"), hindiStudentBearer));
		try (PDDocument document = Loader.loadPDF(pdf)) {
			assertThat(new PDFTextStripper().getText(document)).doesNotContain("DRAFT").contains("Published");
		}

		mockMvc.perform(as(get("/api/v1/students/" + otherStudentId + "/report-card.pdf"), hindiStudentBearer))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/students/" + hindiStudentId + "/report-card.pdf")
						.header("X-School-Id", SCHOOL_ID)
						.header("Authorization", "")
						.param("term", TERM))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void classPdfIsOnePagePerStudentForAdminOrClassTeacherOnly() throws Exception {
		mockMvc.perform(as(get("/api/v1/class-sections/" + sectionId + "/report-cards.pdf"), unrelatedTeacherBearer))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(get("/api/v1/class-sections/" + sectionId + "/report-cards.pdf"), hindiStudentBearer))
				.andExpect(status().isForbidden());

		byte[] pdf = expectPdf(as(get("/api/v1/class-sections/" + sectionId + "/report-cards.pdf"), classTeacherBearer));
		try (PDDocument document = Loader.loadPDF(pdf)) {
			assertThat(document.getNumberOfPages()).isEqualTo(2);
		}
		expectPdf(as(get("/api/v1/class-sections/" + sectionId + "/report-cards.pdf"), adminBearer));

		// The batched section query must produce the same numbers as the per-student one.
		mockMvc.perform(as(get("/api/v1/class-sections/" + sectionId + "/report-cards"), adminBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(2));
		String single = mockMvc.perform(as(get("/api/v1/students/" + hindiStudentId + "/report-card"), adminBearer))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String grid = mockMvc.perform(as(get("/api/v1/class-sections/" + sectionId + "/report-cards"), adminBearer))
				.andReturn().getResponse().getContentAsString();
		Object gridRow = JsonPath.read(grid, "$.data[?(@.studentId == '" + hindiStudentId + "')]");
		assertThat(JsonPath.<Object>read(gridRow, "$[0].totalMarksObtained").toString())
				.isEqualTo(JsonPath.<Object>read(single, "$.data.totalMarksObtained").toString());
		assertThat(JsonPath.<Object>read(gridRow, "$[0].overallGrade").toString())
				.isEqualTo(JsonPath.<Object>read(single, "$.data.overallGrade").toString());
	}

	private void publish() throws Exception {
		mockMvc.perform(as(post("/api/v1/class-sections/" + sectionId + "/report-cards/publish"), adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"term": "%s"}
								""".formatted(TERM)))
				.andExpect(status().isOk());
	}

	private byte[] expectPdf(MockHttpServletRequestBuilder request) throws Exception {
		ResultActions result = mockMvc.perform(request)
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_PDF))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.startsWith("attachment;")));
		byte[] body = result.andReturn().getResponse().getContentAsByteArray();
		assertThat(new String(body, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
		return body;
	}

	private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String bearer) {
		return request
				.header("X-School-Id", SCHOOL_ID)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
				.param("term", TERM);
	}

}
