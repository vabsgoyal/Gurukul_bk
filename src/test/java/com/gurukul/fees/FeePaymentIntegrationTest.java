package com.gurukul.fees;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

	@Test
	void upiQrRequiresSchoolVpaThenGeneratesAScannableLink() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);

		MvcResult schoolResult = mockMvc.perform(post("/api/v1/schools")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "UPI Test School %s",
								  "address": "1 Test Road",
								  "city": "Jaipur",
								  "state": "Rajasthan",
								  "pincode": "302001",
								  "contactEmail": "upi-test-%s@example.com",
								  "contactPhone": "9000000001",
								  "principalName": "Principal %s",
								  "directorName": "Director %s",
								  "principalPhone": "9000000002",
								  "adminPhone": "9000000003"
								}
								""".formatted(suffix, suffix, suffix, suffix)))
				.andExpect(status().isOk())
				.andReturn();
		String schoolId = JsonPath.read(schoolResult.getResponse().getContentAsString(), "$.data.school.id");

		MvcResult sectionResult = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", schoolId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 9", "section": "UPI-%s", "academicYear": "2026-27"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		String classSectionId = JsonPath.read(sectionResult.getResponse().getContentAsString(), "$.data.id");

		MvcResult categoryResult = mockMvc.perform(post("/api/v1/fee-categories")
						.header("X-School-Id", schoolId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code": "TUITION", "name": "Tuition Fee"}
								"""))
				.andExpect(status().isOk())
				.andReturn();
		String categoryId = JsonPath.read(categoryResult.getResponse().getContentAsString(), "$.data.id");

		MvcResult structureResult = mockMvc.perform(post("/api/v1/fee-structures")
						.header("X-School-Id", schoolId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(String.format("""
								{
								  "classSectionId": "%s",
								  "academicYear": "2026-27",
								  "lines": [{"feeCategoryId": "%s", "amount": 8000.00}]
								}
								""", classSectionId, categoryId)))
				.andExpect(status().isOk())
				.andReturn();
		String structureId = JsonPath.read(structureResult.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(post("/api/v1/students")
						.header("X-School-Id", schoolId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(String.format("""
								{
								  "rollNumber": "9A-UPI-001",
								  "name": "UPI Test Student",
								  "dob": "2011-01-01",
								  "gender": "FEMALE",
								  "address": "Test Address",
								  "parentName": "Parent",
								  "parentContact": "9876543211",
								  "classSectionId": "%s",
								  "admissionDate": "2026-04-01"
								}
								""", classSectionId)))
				.andExpect(status().isOk());

		MvcResult assessmentsResult = mockMvc.perform(post("/api/v1/fee-structures/" + structureId + "/generate-assessments")
						.header("X-School-Id", schoolId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andReturn();
		String assessmentId = JsonPath.read(assessmentsResult.getResponse().getContentAsString(), "$.data[0].id");

		// No UPI VPA configured yet - must fail clearly, not silently generate a broken QR.
		mockMvc.perform(post("/api/v1/fee-assessments/" + assessmentId + "/upi-qr")
						.header("X-School-Id", schoolId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(put("/api/v1/schools/" + schoolId)
						.header("X-School-Id", schoolId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(String.format("""
								{
								  "name": "UPI Test School %s",
								  "address": "1 Test Road",
								  "city": "Jaipur",
								  "state": "Rajasthan",
								  "pincode": "302001",
								  "contactEmail": "upi-test-%s@example.com",
								  "contactPhone": "9000000001",
								  "principalName": "Principal %s",
								  "directorName": "Director %s",
								  "upiVpa": "school@upi",
								  "upiPayeeName": "UPI Test School"
								}
								""", suffix, suffix, suffix, suffix)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.upiVpa").value("school@upi"));

		// Defaults amount to the full remaining due when omitted.
		MvcResult qrResult = mockMvc.perform(post("/api/v1/fee-assessments/" + assessmentId + "/upi-qr")
						.header("X-School-Id", schoolId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.payeeVpa").value("school@upi"))
				.andExpect(jsonPath("$.data.payeeName").value("UPI Test School"))
				.andExpect(jsonPath("$.data.amount").value(8000.00))
				.andExpect(jsonPath("$.data.referenceId").exists())
				.andReturn();
		String upiUri = JsonPath.read(qrResult.getResponse().getContentAsString(), "$.data.upiUri");
		String referenceId = JsonPath.read(qrResult.getResponse().getContentAsString(), "$.data.referenceId");
		assertThat(upiUri).startsWith("upi://pay?pa=school%40upi");
		assertThat(upiUri).contains("cu=INR").contains("tr=" + referenceId);

		// Amount over the remaining due is rejected.
		mockMvc.perform(post("/api/v1/fee-assessments/" + assessmentId + "/upi-qr")
						.header("X-School-Id", schoolId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"amount": 999999.00}
								"""))
				.andExpect(status().isBadRequest());

		// A partial amount is honored.
		mockMvc.perform(post("/api/v1/fee-assessments/" + assessmentId + "/upi-qr")
						.header("X-School-Id", schoolId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"amount": 3000.00}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.amount").value(3000.00));
	}

}
