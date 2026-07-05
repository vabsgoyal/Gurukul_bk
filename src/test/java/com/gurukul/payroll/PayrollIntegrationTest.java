package com.gurukul.payroll;

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
class PayrollIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void payrollRunProcessAndPay() throws Exception {
		MvcResult employeeResult = mockMvc.perform(post("/api/v1/employees")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Payroll Teacher", "designation": "Teacher", "joinDate": "2024-04-01"}
								"""))
				.andExpect(status().isOk())
				.andReturn();

		String employeeId = JsonPath.read(employeeResult.getResponse().getContentAsString(), "$.data.id");

		String structurePayload = String.format("""
				{"employeeId": "%s", "basic": 30000.00, "allowances": 5000.00, "deductions": 2000.00, "effectiveFrom": "2024-04-01"}
				""", employeeId);

		mockMvc.perform(post("/api/v1/salary-structures")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(structurePayload))
				.andExpect(status().isOk());

		MvcResult runResult = mockMvc.perform(post("/api/v1/payroll/runs")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"month\": 6, \"year\": 2026}"))
				.andExpect(status().isOk())
				.andReturn();

		String runId = JsonPath.read(runResult.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(post("/api/v1/payroll/runs/" + runId + "/process")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PROCESSED"));

		mockMvc.perform(get("/api/v1/payroll/runs/" + runId + "/lines")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].net").value(33000.00));

		mockMvc.perform(post("/api/v1/payroll/runs/" + runId + "/pay")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"paymentMethod\": \"BANK_TRANSFER\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PAID"));
	}

}
