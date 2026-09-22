package com.gurukul.students;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StudentBulkImportIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final String SEED_CLASS_SECTION_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void bulkImportReportsPerRowOutcomeAndDoesNotRollBackGoodRowsOnABadOne() throws Exception {
		String bearer = "Bearer " + AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);

		String payload = """
				{
				  "students": [
				    {
				      "name": "Good Row One",
				      "dob": "2012-05-15",
				      "gender": "MALE",
				      "address": "Kalalkhedi",
				      "parentName": "Ramprasad",
				      "parentContact": "9876543210",
				      "classSectionId": "%s",
				      "admissionDate": "2026-04-01"
				    },
				    {
				      "name": "Bad Row Missing Dob",
				      "gender": "MALE",
				      "address": "Paslod",
				      "parentName": "Ashok",
				      "parentContact": "9876543211",
				      "classSectionId": "%s",
				      "admissionDate": "2026-04-01"
				    },
				    {
				      "name": "Good Row Two",
				      "dob": "2013-01-10",
				      "gender": "FEMALE",
				      "address": "Sandla",
				      "parentName": "Santosh",
				      "parentContact": "9876543212",
				      "classSectionId": "%s",
				      "admissionDate": "2026-04-01"
				    }
				  ]
				}
				""".formatted(SEED_CLASS_SECTION_ID, SEED_CLASS_SECTION_ID, SEED_CLASS_SECTION_ID);

		mockMvc.perform(post("/api/v1/students/bulk")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(payload))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.total").value(3))
				.andExpect(jsonPath("$.data.succeeded").value(2))
				.andExpect(jsonPath("$.data.failed").value(1))
				.andExpect(jsonPath("$.data.results[0].success").value(true))
				.andExpect(jsonPath("$.data.results[1].success").value(false))
				.andExpect(jsonPath("$.data.results[1].error").value(org.hamcrest.Matchers.containsString("dob")))
				.andExpect(jsonPath("$.data.results[2].success").value(true));
	}

	@Test
	void bulkImportWithoutAdminIsForbidden() throws Exception {
		String payload = """
				{
				  "students": [
				    {
				      "name": "No Auth Student",
				      "dob": "2012-05-15",
				      "gender": "MALE",
				      "address": "Kalalkhedi",
				      "parentName": "Ramprasad",
				      "parentContact": "9876543213",
				      "classSectionId": "%s",
				      "admissionDate": "2026-04-01"
				    }
				  ]
				}
				""".formatted(SEED_CLASS_SECTION_ID);

		mockMvc.perform(post("/api/v1/students/bulk")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(payload))
				.andExpect(status().isForbidden());
	}

	@Test
	void sensitiveFieldsRoundTripEncryptedAndAreAdminOnly() throws Exception {
		String bearer = "Bearer " + AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);

		String enrollStudent = """
				{
				  "name": "RTE Student",
				  "dob": "2016-09-23",
				  "gender": "FEMALE",
				  "address": "Kalalkhedi",
				  "parentName": "Ramprasad Bodana",
				  "parentContact": "8120097001",
				  "classSectionId": "%s",
				  "admissionDate": "2021-07-14",
				  "sssmId": "310755266",
				  "aadhaarNumber": "349877211234",
				  "caste": "Bagri",
				  "category": "SC",
				  "annualIncome": 100000,
				  "bankAccountNumber": "50100381240650",
				  "bankIfsc": "HDFC0000908"
				}
				""".formatted(SEED_CLASS_SECTION_ID);

		MvcResult createResult = mockMvc.perform(post("/api/v1/students")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(enrollStudent))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.aadhaarNumber").value("349877211234"))
				.andExpect(jsonPath("$.data.bankAccountNumber").value("50100381240650"))
				.andReturn();
		String studentId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.id");

		// Admin, reading it back later: decrypted values returned.
		mockMvc.perform(get("/api/v1/students/" + studentId)
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.aadhaarNumber").value("349877211234"))
				.andExpect(jsonPath("$.data.caste").value("Bagri"))
				.andExpect(jsonPath("$.data.bankAccountNumber").value("50100381240650"));

		// No admin principal on this request: every RTE/sensitive field is null, same as registrationNumber.
		mockMvc.perform(get("/api/v1/students/" + studentId)
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.aadhaarNumber").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.data.caste").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.data.bankAccountNumber").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.data.registrationNumber").value(org.hamcrest.Matchers.nullValue()));
	}

}
