package com.gurukul.sponsorships;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SponsorshipIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void sponsorshipPaymentFlow() throws Exception {
		MvcResult sponsorResult = mockMvc.perform(post("/api/v1/sponsors")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"Acme Corp\"}"))
				.andExpect(status().isOk())
				.andReturn();

		String sponsorId = JsonPath.read(sponsorResult.getResponse().getContentAsString(), "$.data.id");

		String sponsorshipPayload = String.format("""
				{"sponsorId": "%s", "purpose": "SPORTS", "pledgedAmount": 50000.00}
				""", sponsorId);

		MvcResult sponsorshipResult = mockMvc.perform(post("/api/v1/sponsorships")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(sponsorshipPayload))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PLEDGED"))
				.andReturn();

		String sponsorshipId = JsonPath.read(sponsorshipResult.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(post("/api/v1/sponsorships/" + sponsorshipId + "/payments")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"amount\": 20000.00, \"paymentMethod\": \"BANK_TRANSFER\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.receiptNumber").exists());

		mockMvc.perform(post("/api/v1/sponsorships/" + sponsorshipId + "/payments")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"amount\": 30000.00, \"paymentMethod\": \"BANK_TRANSFER\"}"))
				.andExpect(status().isOk());
	}

}
