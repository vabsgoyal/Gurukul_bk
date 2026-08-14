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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the PaymentAttempt lifecycle: created as INITIATED before the UPI app ever opens,
 * transitions to RESPONSE_SUCCESS marks the fee PAID (prototype-only, see
 * app.fees.unverified-upi-auto-mark-paid), and a repeated RESPONSE_SUCCESS report for the same
 * attempt does not double-record the payment.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentAttemptIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void paymentAttemptLifecycleAndIdempotentSuccessReporting() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);

		mockMvc.perform(put("/api/v1/schools/" + SCHOOL_ID)
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Gurukul Demo School", "address": "123 Education Lane", "city": "Jaipur",
								 "state": "Rajasthan", "pincode": "302001", "contactEmail": "admin@gurukul.demo",
								 "contactPhone": "9876543210", "principalName": "Dr. Meena Sharma",
								 "directorName": "Mr. Rajesh Kumar", "bankAccountNumber": "123456789012",
								 "bankIfsc": "SBIN0001234", "bankAccountHolderName": "Gurukul Demo School"}
								"""))
				.andExpect(status().isOk());

		MvcResult sectionResult = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 8", "section": "PA-%s", "academicYear": "2026-27"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		String classSectionId = JsonPath.read(sectionResult.getResponse().getContentAsString(), "$.data.id");

		MvcResult categoryResult = mockMvc.perform(post("/api/v1/fee-categories")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code": "TUITION-PA-%s", "name": "Tuition Fee"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		String categoryId = JsonPath.read(categoryResult.getResponse().getContentAsString(), "$.data.id");

		MvcResult structureResult = mockMvc.perform(post("/api/v1/fee-structures")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"classSectionId": "%s", "academicYear": "2026-27",
								 "lines": [{"feeCategoryId": "%s", "amount": 9000.00}]}
								""".formatted(classSectionId, categoryId)))
				.andExpect(status().isOk())
				.andReturn();
		String structureId = JsonPath.read(structureResult.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(post("/api/v1/students")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"rollNumber": "PA-%s", "name": "Payment Attempt Test Student", "dob": "2012-01-01",
								 "gender": "MALE", "address": "Test Address", "parentName": "Parent",
								 "parentContact": "9876500000", "classSectionId": "%s", "admissionDate": "2026-04-01"}
								""".formatted(suffix, classSectionId)))
				.andExpect(status().isOk());

		MvcResult assessmentsResult = mockMvc.perform(post("/api/v1/fee-structures/" + structureId + "/generate-assessments")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andReturn();
		String assessmentId = JsonPath.read(assessmentsResult.getResponse().getContentAsString(), "$.data[0].id");

		// No pending attempt yet.
		mockMvc.perform(get("/api/v1/fee-assessments/" + assessmentId + "/payment-attempts/pending")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").doesNotExist());

		// Creating a payment request persists an INITIATED attempt before any UPI app opens.
		MvcResult paymentRequestResult = mockMvc.perform(post("/api/v1/fee-assessments/" + assessmentId + "/payment-request")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.amount").value(9000.00))
				.andReturn();
		String transactionRef = JsonPath.read(paymentRequestResult.getResponse().getContentAsString(), "$.data.referenceId");

		mockMvc.perform(get("/api/v1/fee-assessments/" + assessmentId + "/payment-attempts")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].status").value("INITIATED"))
				.andExpect(jsonPath("$.data[0].transactionRef").value(transactionRef));

		// Now there IS a pending attempt - the client should warn before starting another.
		mockMvc.perform(get("/api/v1/fee-assessments/" + assessmentId + "/payment-attempts/pending")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.transactionRef").value(transactionRef));

		// The UPI app claims success (unverified) - fee gets marked PAID, attempt stays
		// RESPONSE_SUCCESS (not VERIFIED - no gateway has actually confirmed this).
		mockMvc.perform(post("/api/v1/payment-attempts/" + transactionRef + "/result")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status": "RESPONSE_SUCCESS", "upiTransactionId": "UPI123456789",
								 "responseCode": "00", "rawResponse": "Status=SUCCESS&txnId=UPI123456789&responseCode=00"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("RESPONSE_SUCCESS"))
				.andExpect(jsonPath("$.data.upiTransactionId").value("UPI123456789"));

		mockMvc.perform(get("/api/v1/fee-assessments").param("size", "1000").header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[?(@.id=='" + assessmentId + "')].status").value("PAID"));

		// No more pending attempt - it resolved.
		mockMvc.perform(get("/api/v1/fee-assessments/" + assessmentId + "/payment-attempts/pending")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").doesNotExist());

		// Reporting RESPONSE_SUCCESS again for the same attempt (e.g. a retried request) must not
		// double-record the payment.
		mockMvc.perform(post("/api/v1/payment-attempts/" + transactionRef + "/result")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status": "RESPONSE_SUCCESS", "upiTransactionId": "UPI123456789", "responseCode": "00"}
								"""))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/fee-assessments").param("size", "1000").header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[?(@.id=='" + assessmentId + "')].status").value("PAID"))
				.andExpect(jsonPath("$.data.content[?(@.id=='" + assessmentId + "')].totalPaid").value(9000.00));
	}

}
