package com.gurukul.finance;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FinanceFoundationIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void financeEndpointsRequireSchoolIdHeader() throws Exception {
		mockMvc.perform(get("/api/v1/finance/summary"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false));
	}

	@Test
	void createVendorEmployeeEventAndRecordTransactions() throws Exception {
		String vendorPayload = """
				{
				  "name": "Test Vendor",
				  "contactPhone": "9876543210"
				}
				""";

		mockMvc.perform(post("/api/v1/vendors")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(vendorPayload))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.name").value("Test Vendor"));

		String employeePayload = """
				{
				  "name": "Test Teacher",
				  "designation": "Teacher",
				  "joinDate": "2024-04-01"
				}
				""";

		mockMvc.perform(post("/api/v1/employees")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(employeePayload))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"));

		String eventPayload = """
				{
				  "name": "Sports Day",
				  "eventDate": "2026-11-01",
				  "inflowEnabled": true,
				  "outflowEnabled": true
				}
				""";

		mockMvc.perform(post("/api/v1/events")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventPayload))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.name").value("Sports Day"));

		String summaryBefore = mockMvc.perform(get("/api/v1/finance/summary").header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		double inflowBefore = JsonPath.read(summaryBefore, "$.data.totalInflow");
		double outflowBefore = JsonPath.read(summaryBefore, "$.data.totalOutflow");

		String inflowPayload = """
				{
				  "direction": "INFLOW",
				  "sourceType": "MANUAL",
				  "amount": 5000.00,
				  "paymentMethod": "CASH",
				  "academicYear": "2026-27"
				}
				""";

		MvcResult inflowResult = mockMvc.perform(post("/api/v1/finance/transactions")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(inflowPayload))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.receiptNumber").exists())
				.andReturn();

		String receiptNumber = JsonPath.read(
				inflowResult.getResponse().getContentAsString(), "$.data.receiptNumber");
		org.assertj.core.api.Assertions.assertThat(receiptNumber).contains("RCPT");

		String outflowPayload = """
				{
				  "direction": "OUTFLOW",
				  "sourceType": "MANUAL",
				  "amount": 2000.00,
				  "paymentMethod": "UPI"
				}
				""";

		mockMvc.perform(post("/api/v1/finance/transactions")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(outflowPayload))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/finance/summary").header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalInflow").value(inflowBefore + 5000.00))
				.andExpect(jsonPath("$.data.totalOutflow").value(outflowBefore + 2000.00))
				.andExpect(jsonPath("$.data.netBalance").value(inflowBefore - outflowBefore + 3000.00));

		mockMvc.perform(get("/api/v1/finance/transactions").header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(2)));
	}

}
