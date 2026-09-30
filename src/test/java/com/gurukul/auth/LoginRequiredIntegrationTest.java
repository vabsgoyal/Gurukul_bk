package com.gurukul.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every route needs a login unless SecurityConfig lists it as public. Pins both halves: a school's
 * data can't be read or written anonymously, and everything the app calls before login still works.
 */
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(CapturingOtpChannel.class)
class LoginRequiredIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void aSchoolsDataNeedsALogin() throws Exception {
		String studentId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID,
				"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "Private Student");

		for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
				get("/api/v1/students"),
				get("/api/v1/students/" + studentId),
				get("/api/v1/employees"),
				get("/api/v1/class-sections"),
				get("/api/v1/events"),
				get("/api/v1/schools/" + SCHOOL_ID),
				post("/api/v1/employees").contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"Intruder\", \"designation\": \"Teacher\", \"joinDate\": \"2026-01-01\"}")}) {
			mockMvc.perform(anonymous(request)).andExpect(status().isUnauthorized());
		}
	}

	@Test
	void aTokenForAnotherSchoolIsNotALoginHere() throws Exception {
		String registered = mockMvc.perform(post("/api/v1/schools").header(HttpHeaders.AUTHORIZATION, "")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Other School", "address": "1 Other Road", "city": "Jaipur", "state": "Rajasthan",
								 "pincode": "302001", "contactEmail": "other@school.example", "contactPhone": "9000000002",
								 "principalName": "Other Principal", "directorName": "Other Director",
								 "principalPhone": "9000000002", "adminPhone": "8000000002"}
								"""))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String otherSchoolToken = JsonPath.read(registered, "$.data.principal.token");

		mockMvc.perform(get("/api/v1/students").header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherSchoolToken))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void everythingBeforeLoginStillWorksWithoutOne() throws Exception {
		mockMvc.perform(anonymous(get("/actuator/health"))).andExpect(status().isOk());
		mockMvc.perform(anonymous(get("/api/v1/schools"))).andExpect(status().isOk());
		// Reaching the OTP service at all is the point: an unknown number is its own 404, not a 401.
		mockMvc.perform(anonymous(post("/api/v1/auth/otp/request")).contentType(MediaType.APPLICATION_JSON)
						.content("{\"phone\": \"9876500001\"}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(anonymous(post("/api/v1/auth/login")).contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\": \"admin\", \"password\": \"admin123\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(anonymous(post("/api/v1/auth/refresh")).contentType(MediaType.APPLICATION_JSON)
						.content("{\"refreshToken\": \"unknown\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Session expired - please log in again"));
		mockMvc.perform(anonymous(post("/api/v1/register/parent")).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}

	/** Explicitly no login - DefaultTestLogin would otherwise act as the school's admin. */
	private static MockHttpServletRequestBuilder anonymous(MockHttpServletRequestBuilder request) {
		return request.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "");
	}

}
