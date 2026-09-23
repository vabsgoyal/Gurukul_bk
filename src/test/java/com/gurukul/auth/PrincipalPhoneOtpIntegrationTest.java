package com.gurukul.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingOtpChannel.class)
class PrincipalPhoneOtpIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final String PRINCIPAL_PHONE = "9999999999";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CapturingOtpChannel otpChannel;

	@Test
	void principalPhoneLogsInAsAdminViaOtp() throws Exception {
		mockMvc.perform(post("/api/v1/auth/otp/request")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"phone\": \"" + PRINCIPAL_PHONE + "\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/auth/otp/verify")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"phone\": \"" + PRINCIPAL_PHONE + "\", \"otp\": \""
								+ otpChannel.lastCodeFor(PRINCIPAL_PHONE) + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.role").value("ADMIN"))
				.andExpect(jsonPath("$.data.token").exists());
	}

}
