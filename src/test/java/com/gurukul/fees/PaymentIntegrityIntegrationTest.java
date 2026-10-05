package com.gurukul.fees;

import com.gurukul.auth.AuthTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With default settings a payer's own "UPI success" never marks a fee paid, and two payments
 * recorded at the same moment can't both pass the remaining-due check.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentIntegrityIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired private MockMvc mockMvc;

	private String admin;
	private String assessmentId;

	@BeforeEach
	void setUp() throws Exception {
		admin = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		mockMvc.perform(put("/api/v1/schools/" + SCHOOL_ID).header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Gurukul Demo School", "address": "123 Education Lane", "city": "Jaipur",
								 "state": "Rajasthan", "pincode": "302001", "contactEmail": "admin@gurukul.demo",
								 "contactPhone": "9876543210", "principalName": "Dr. Meena Sharma",
								 "directorName": "Mr. Rajesh Kumar", "bankAccountNumber": "123456789012",
								 "bankIfsc": "SBIN0001234", "bankAccountHolderName": "Gurukul Demo School"}
								"""))
				.andExpect(status().isOk());
		String sectionId = id(post("/api/v1/class-sections"), """
				{"className": "Grade 8", "section": "PI-%s", "academicYear": "2026-27"}
				""".formatted(suffix));
		String categoryId = id(post("/api/v1/fee-categories"), """
				{"code": "TUITION-PI-%s", "name": "Tuition Fee"}
				""".formatted(suffix));
		String structureId = id(post("/api/v1/fee-structures"), """
				{"classSectionId": "%s", "academicYear": "2026-27", "lines": [{"feeCategoryId": "%s", "amount": 9000.00}]}
				""".formatted(sectionId, categoryId));
		AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, "Payment Integrity Student");
		String generated = mockMvc.perform(post("/api/v1/fee-structures/" + structureId + "/generate-assessments")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assessmentId = JsonPath.read(generated, "$.data[0].id");
	}

	@Test
	void aClaimedUpiSuccessIsRecordedButDoesNotMarkTheFeePaid() throws Exception {
		String ref = JsonPath.read(mockMvc.perform(post("/api/v1/fee-assessments/" + assessmentId + "/payment-request")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.referenceId");

		mockMvc.perform(post("/api/v1/payment-attempts/" + ref + "/result").header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\": \"RESPONSE_SUCCESS\", \"upiTransactionId\": \"UPI-CLAIMED\", \"responseCode\": \"00\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("RESPONSE_SUCCESS"));

		mockMvc.perform(get("/api/v1/fee-assessments").param("size", "1000").header("X-School-Id", SCHOOL_ID))
				.andExpect(jsonPath("$.data[?(@.id=='" + assessmentId + "')].status").value("UNPAID"))
				.andExpect(jsonPath("$.data[?(@.id=='" + assessmentId + "')].totalPaid").value(0.0));
	}

	@Test
	void staffConfirmASelfReportedUpiPaymentOnceByItsReference() throws Exception {
		String ref = JsonPath.read(mockMvc.perform(post("/api/v1/fee-assessments/" + assessmentId + "/payment-request")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.referenceId");
		mockMvc.perform(post("/api/v1/payment-attempts/" + ref + "/result").header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\": \"RESPONSE_SUCCESS\", \"upiTransactionId\": \"UPI-CLAIMED\"}"))
				.andExpect(status().isOk());

		// Staff have seen the money and confirm it, citing the attempt's reference.
		String confirm = """
				{"assessmentId": "%s", "amount": 4000.00, "paymentMethod": "UPI", "paymentReference": "%s"}
				""".formatted(assessmentId, ref);
		mockMvc.perform(post("/api/v1/fee-payments").header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON).content(confirm))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/fee-assessments/" + assessmentId + "/payment-attempts").header("X-School-Id", SCHOOL_ID))
				.andExpect(jsonPath("$.data[0].status").value("VERIFIED"));

		// The same attempt can't be confirmed twice, even for an amount that would still fit.
		mockMvc.perform(post("/api/v1/fee-payments").header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON).content(confirm))
				.andExpect(status().isBadRequest());

		// And the payer's app can't overwrite a confirmed attempt.
		mockMvc.perform(post("/api/v1/payment-attempts/" + ref + "/result").header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\": \"FAILED\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("VERIFIED"));

		mockMvc.perform(get("/api/v1/fee-assessments").param("size", "1000").header("X-School-Id", SCHOOL_ID))
				.andExpect(jsonPath("$.data[?(@.id=='" + assessmentId + "')].status").value("PARTIAL"))
				.andExpect(jsonPath("$.data[?(@.id=='" + assessmentId + "')].totalPaid").value(4000.0));
	}

	@Test
	void twoPaymentsAtTheSameMomentCannotBothBeRecorded() throws Exception {
		String fullAmount = """
				{"assessmentId": "%s", "amount": 9000.00, "paymentMethod": "CASH"}
				""".formatted(assessmentId);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		List<Future<Integer>> results = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			Callable<Integer> pay = () -> {
				start.await();
				return mockMvc.perform(post("/api/v1/fee-payments").header("X-School-Id", SCHOOL_ID)
								.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
								.contentType(MediaType.APPLICATION_JSON).content(fullAmount))
						.andReturn().getResponse().getStatus();
			};
			results.add(pool.submit(pay));
		}
		start.countDown();
		List<Integer> statuses = new ArrayList<>();
		for (Future<Integer> result : results) {
			statuses.add(result.get());
		}
		pool.shutdown();

		assertThat(statuses).containsExactlyInAnyOrder(200, 400);
		mockMvc.perform(get("/api/v1/fee-assessments").param("size", "1000").header("X-School-Id", SCHOOL_ID))
				.andExpect(jsonPath("$.data[?(@.id=='" + assessmentId + "')].totalPaid").value(9000.0))
				.andExpect(jsonPath("$.data[?(@.id=='" + assessmentId + "')].status").value("PAID"));
	}

	private String id(MockHttpServletRequestBuilder request, String body) throws Exception {
		String response = mockMvc.perform(request.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.data.id");
	}

}
