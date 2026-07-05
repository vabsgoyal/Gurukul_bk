package com.gurukul.collections;

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
class EventCollectionIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void eventCollectionFlow() throws Exception {
		String eventPayload = """
				{"name": "Annual Day", "eventDate": "2026-12-01", "inflowEnabled": true}
				""";

		MvcResult eventResult = mockMvc.perform(post("/api/v1/events")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventPayload))
				.andExpect(status().isOk())
				.andReturn();

		String eventId = JsonPath.read(eventResult.getResponse().getContentAsString(), "$.data.id");

		String feePayload = """
				{"participantType": "STUDENT", "amount": 200.00}
				""";

		mockMvc.perform(post("/api/v1/events/" + eventId + "/participation-fees")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(feePayload))
				.andExpect(status().isOk());

		String collectionPayload = """
				{"payerName": "Rahul", "amount": 200.00, "paymentMethod": "CASH"}
				""";

		mockMvc.perform(post("/api/v1/events/" + eventId + "/collections")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(collectionPayload))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.receiptNumber").exists());

		mockMvc.perform(get("/api/v1/events/" + eventId + "/balance").header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalCollections").value(200.00))
				.andExpect(jsonPath("$.data.netBalance").value(200.00));
	}

}
