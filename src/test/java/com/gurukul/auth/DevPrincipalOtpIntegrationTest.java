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
class DevPrincipalOtpIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final String DEV_PRINCIPAL_PHONE = "9999999999";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void devPrincipalPhoneLogsInAsAdminViaOtp() throws Exception {
		mockMvc.perform(post("/api/v1/auth/otp/verify")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"phone\": \"" + DEV_PRINCIPAL_PHONE + "\", \"otp\": \"1234\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.role").value("ADMIN"))
				.andExpect(jsonPath("$.data.token").exists());
	}

}
