package com.gurukul.expenses.infrastructure;

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
class InfraExpenseIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final String CATEGORY_ID = "33333333-3333-3333-3333-333333333331";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void infraExpenseWorkflow() throws Exception {
		String createPayload = String.format("""
				{"categoryId": "%s", "description": "New books", "estimatedAmount": 15000.00}
				""", CATEGORY_ID);

		MvcResult createResult = mockMvc.perform(post("/api/v1/infra-expense-requests")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createPayload))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("DRAFT"))
				.andReturn();

		String requestId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(post("/api/v1/infra-expense-requests/" + requestId + "/submit")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("SUBMITTED"));

		mockMvc.perform(post("/api/v1/infra-expense-requests/" + requestId + "/approve")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("APPROVED"));

		MvcResult vendorResult = mockMvc.perform(post("/api/v1/vendors")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"Book Vendor\"}"))
				.andExpect(status().isOk())
				.andReturn();

		String vendorId = JsonPath.read(vendorResult.getResponse().getContentAsString(), "$.data.id");

		String purchasePayload = String.format("""
				{"vendorId": "%s", "invoiceNumber": "INV-001", "actualAmount": 14500.00}
				""", vendorId);

		mockMvc.perform(post("/api/v1/infra-expense-requests/" + requestId + "/purchase")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(purchasePayload))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PURCHASED"));

		mockMvc.perform(post("/api/v1/infra-expense-requests/" + requestId + "/pay")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"paymentMethod\": \"BANK_TRANSFER\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PAID"));

		mockMvc.perform(get("/api/v1/finance/summary").header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalOutflow").value(14500.00));
	}

}
