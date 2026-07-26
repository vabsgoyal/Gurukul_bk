package com.gurukul.auth;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public final class AuthTestSupport {

	public static final String DEV_ADMIN_USERNAME = "admin";
	public static final String DEV_ADMIN_PASSWORD = "admin123";

	private AuthTestSupport() {
	}

	public static String loginAsDevAdmin(MockMvc mockMvc, String schoolId) throws Exception {
		return login(mockMvc, schoolId, DEV_ADMIN_USERNAME, DEV_ADMIN_PASSWORD);
	}

	public static String login(MockMvc mockMvc, String schoolId, String username, String password) throws Exception {
		var result = mockMvc.perform(post("/api/v1/auth/login")
						.header("X-School-Id", schoolId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username": "%s", "password": "%s"}
								""".formatted(username, password)))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
	}

}
