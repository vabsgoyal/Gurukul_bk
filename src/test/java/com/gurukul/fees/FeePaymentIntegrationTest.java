package com.gurukul.fees;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FeePaymentIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final String CLASS_SECTION_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void feeStructureAssessmentAndPartialPaymentFlow() throws Exception {
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
				""", CLASS_SECTION_ID, categoryId);

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
				""".formatted(CLASS_SECTION_ID);

		mockMvc.perform(post("/api/v1/students")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(enrollPayload))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/fee-structures/" + structureId + "/generate-assessments")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].status").value("UNPAID"))
				.andExpect(jsonPath("$.data[0].totalDue").value(10000.00));

		String assessmentId = JsonPath.read(
				mockMvc.perform(get("/api/v1/fee-assessments").header("X-School-Id", SCHOOL_ID))
						.andExpect(status().isOk())
						.andReturn().getResponse().getContentAsString(),
				"$.data[0].id");

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

		mockMvc.perform(get("/api/v1/fee-assessments").header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].status").value("PARTIAL"))
				.andExpect(jsonPath("$.data[0].totalPaid").value(4000.00));

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

		mockMvc.perform(get("/api/v1/fee-assessments").header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].status").value("PAID"));

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

}
