package com.gurukul.leads;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With LEADS_ADMIN_TOKEN unset (the default), the listing behaves as if the route didn't exist. */
@SpringBootTest
@AutoConfigureMockMvc
class LeadListingDisabledIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void listingIs404WhenNoTokenConfigured() throws Exception {
		mockMvc.perform(get("/api/v1/leads")).andExpect(status().isNotFound());
		// An empty bearer must not match an empty configured token.
		mockMvc.perform(get("/api/v1/leads").header(HttpHeaders.AUTHORIZATION, "Bearer "))
				.andExpect(status().isNotFound());
	}

}
