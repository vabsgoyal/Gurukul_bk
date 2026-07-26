package com.gurukul.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DemoPrincipalSeedIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void seededPrincipalLogsInWithPassword() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\": \"9999999999\", \"password\": \"Principal@9999\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.role").value("ADMIN"))
				.andExpect(jsonPath("$.data.token").exists());
	}

	@Test
	void seededPrincipalRejectsWrongPassword() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\": \"9999999999\", \"password\": \"wrong\"}"))
				.andExpect(status().isUnauthorized());
	}

}
