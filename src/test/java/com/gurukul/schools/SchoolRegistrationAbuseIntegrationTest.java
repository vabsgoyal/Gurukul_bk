package com.gurukul.schools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/v1/schools is public and creates a tenant with two ADMIN logins, so it refuses
 * duplicates (409) and caps registrations per address (429). This context uses a limit of 2 so the
 * cap is reachable; every other test class runs with a high limit (see the surefire config).
 */
@SpringBootTest(properties = "app.schools.max-registrations-per-ip-per-hour=2")
@AutoConfigureMockMvc
class SchoolRegistrationAbuseIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void sameNameInSamePincodeIsRejectedWith409() throws Exception {
		String ip = "203.0.113." + SchoolTestSupport.randomDigits(2);
		String name = "Duplicate Name School " + SchoolTestSupport.randomDigits(6);
		mockMvc.perform(SchoolTestSupport.registration(name, "302001", phone('9'), phone('8')).with(from(ip)))
				.andExpect(status().isOk());

		// Different case and phones, same school.
		mockMvc.perform(SchoolTestSupport.registration(name.toUpperCase(), "302001", phone('9'), phone('8'))
						.with(from(ip + "1")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("SCHOOL_ALREADY_REGISTERED"))
				.andExpect(jsonPath("$.message").value(containsString("already registered in pincode 302001")));

		// Same name in another pincode is a different school.
		mockMvc.perform(SchoolTestSupport.registration(name, "110001", phone('9'), phone('8')).with(from(ip + "2")))
				.andExpect(status().isOk());
	}

	@Test
	void aPhoneThatIsAlreadyASchoolAdminIsRejectedWith409() throws Exception {
		String adminPhone = phone('8');
		mockMvc.perform(SchoolTestSupport.registration("Phone Owner School " + SchoolTestSupport.randomDigits(6),
						"302001", phone('9'), adminPhone).with(from("198.51.100.10")))
				.andExpect(status().isOk());

		// The same person as principal of a "new" school.
		mockMvc.perform(SchoolTestSupport.registration("Phone Reuse School " + SchoolTestSupport.randomDigits(6),
						"302001", adminPhone, phone('8')).with(from("198.51.100.11")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("SCHOOL_ALREADY_REGISTERED"))
				.andExpect(jsonPath("$.message").value(containsString("already an admin login")));
	}

	@Test
	void registrationsArePerAddressRateLimited() throws Exception {
		String ip = "192.0.2." + SchoolTestSupport.randomDigits(2);
		for (int i = 0; i < 2; i++) {
			mockMvc.perform(SchoolTestSupport.registration("Rate Limit School " + SchoolTestSupport.randomDigits(6),
							"302001", phone('9'), phone('8')).with(from(ip)))
					.andExpect(status().isOk());
		}
		mockMvc.perform(SchoolTestSupport.registration("Rate Limit School " + SchoolTestSupport.randomDigits(6),
						"302001", phone('9'), phone('8')).with(from(ip)))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.errorCode").value("RATE_LIMITED"));

		// A spoofed first X-Forwarded-For entry doesn't dodge it: nginx's appended address counts.
		mockMvc.perform(SchoolTestSupport.registration("Rate Limit School " + SchoolTestSupport.randomDigits(6),
						"302001", phone('9'), phone('8'))
						.header("X-Forwarded-For", "10.9.9.9, " + ip))
				.andExpect(status().isTooManyRequests());

		// Another address is unaffected.
		mockMvc.perform(SchoolTestSupport.registration("Rate Limit School " + SchoolTestSupport.randomDigits(6),
						"302001", phone('9'), phone('8')).with(from(ip + "9")))
				.andExpect(status().isOk());
	}

	private static String phone(char first) {
		return first + SchoolTestSupport.randomDigits(9);
	}

	private static org.springframework.test.web.servlet.request.RequestPostProcessor from(String ip) {
		return request -> {
			request.addHeader("X-Forwarded-For", ip);
			return request;
		};
	}

}
