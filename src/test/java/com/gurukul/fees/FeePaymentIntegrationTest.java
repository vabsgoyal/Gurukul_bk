package com.gurukul.fees;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FeePaymentIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void feeStructureAssessmentAndPartialPaymentFlow() throws Exception {
		String sectionSuffix = UUID.randomUUID().toString().substring(0, 8);
		MvcResult sectionResult = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 8", "section": "FEE-%s", "academicYear": "2026-27"}
								""".formatted(sectionSuffix)))
				.andExpect(status().isOk())
				.andReturn();
		String classSectionId = JsonPath.read(sectionResult.getResponse().getContentAsString(), "$.data.id");

		String categoryPayload = """
				{"code": "TUITION", "name": "Tuition Fee"}
				""";

		MvcResult categoryResult = mockMvc.perform(post("/api/v1/fee-categories")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(categoryPayload))
				.andExpect(status().isOk())
				.andReturn();

		String categoryId = JsonPath.read(categoryResult.getResponse().getContentAsString(), "$.data.id");

		String structurePayload = String.format("""
				{
				  "classSectionId": "%s",
				  "academicYear": "2026-27",
				  "lines": [{"feeCategoryId": "%s", "amount": 10000.00}]
				}
				""", classSectionId, categoryId);

		MvcResult structureResult = mockMvc.perform(post("/api/v1/fee-structures")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(structurePayload))
				.andExpect(status().isOk())
				.andReturn();

		String structureId = JsonPath.read(structureResult.getResponse().getContentAsString(), "$.data.id");

		MvcResult assessmentsResult = mockMvc.perform(post("/api/v1/fee-structures/" + structureId + "/generate-assessments")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(0))
				.andReturn();

		String enrollPayload = """
				{
				  "rollNumber": "8A-FEE-001",
				  "name": "Fee Test Student",
				  "dob": "2012-01-01",
				  "gender": "MALE",
				  "address": "Test Address",
				  "parentName": "Parent",
				  "parentContact": "9876543210",
				  "classSectionId": "%s",
				  "admissionDate": "2026-04-01"
				}
				""".formatted(classSectionId);

		MvcResult enrollResult = mockMvc.perform(post("/api/v1/students")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(enrollPayload))
				.andExpect(status().isOk())
				.andReturn();
		String studentId = JsonPath.read(enrollResult.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(post("/api/v1/fee-structures/" + structureId + "/generate-assessments")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].status").value("UNPAID"))
				.andExpect(jsonPath("$.data[0].totalDue").value(10000.00));

		// GET /fee-assessments lists every assessment for the whole school, not just this test's -
		// filter by studentId rather than assuming index [0], since other tests share this school id.
		String assessmentId = (String) findAssessmentForStudent(studentId).get("id");

		String partialPayment = String.format("""
				{
				  "assessmentId": "%s",
				  "amount": 4000.00,
				  "paymentMethod": "CASH"
				}
				""", assessmentId);

		mockMvc.perform(post("/api/v1/fee-payments")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(partialPayment))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.receiptNumber").exists());

		Map<String, Object> afterPartialPayment = findAssessmentForStudent(studentId);
		assertThat(afterPartialPayment.get("status")).isEqualTo("PARTIAL");
		assertThat(((Number) afterPartialPayment.get("totalPaid")).doubleValue()).isEqualTo(4000.00);

		String fullPayment = String.format("""
				{
				  "assessmentId": "%s",
				  "amount": 6000.00,
				  "paymentMethod": "UPI"
				}
				""", assessmentId);

		mockMvc.perform(post("/api/v1/fee-payments")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(fullPayment))
				.andExpect(status().isOk());

		assertThat(findAssessmentForStudent(studentId).get("status")).isEqualTo("PAID");

		String overpay = String.format("""
				{
				  "assessmentId": "%s",
				  "amount": 1.00,
				  "paymentMethod": "CASH"
				}
				""", assessmentId);

		mockMvc.perform(post("/api/v1/fee-payments")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(overpay))
				.andExpect(status().isBadRequest());
	}

	/**
	 * GET /fee-assessments has no filter params and lists every assessment for the whole school, not
	 * just this test's - other tests share this same school id, so index [0] isn't reliable. JsonPath
	 * filter predicates (e.g. "$.data[?(@.studentId=='x')]") always evaluate to a List even with a
	 * trailing index, so this filters in plain Java instead.
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> findAssessmentForStudent(String studentId) throws Exception {
		String body = mockMvc.perform(get("/api/v1/fee-assessments").header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		List<Map<String, Object>> assessments = JsonPath.read(body, "$.data");
		return assessments.stream()
				.filter(a -> studentId.equals(a.get("studentId")))
				.findFirst()
				.orElseThrow(() -> new AssertionError("No fee assessment found for studentId " + studentId));
	}

}
