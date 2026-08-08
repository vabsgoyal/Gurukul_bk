package com.gurukul.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Default config (app.auth.supabase.enabled defaults to false) - the new endpoint must refuse to do anything. */
@SpringBootTest
@AutoConfigureMockMvc
class SupabaseAuthDisabledIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void endpointRefusesWhenFeatureDisabled() throws Exception {
		mockMvc.perform(post("/api/v1/auth/otp/session")
						.header("X-School-Id", SCHOOL_ID)
						.header("Authorization", "Bearer whatever")
						.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isForbidden());
	}

}
