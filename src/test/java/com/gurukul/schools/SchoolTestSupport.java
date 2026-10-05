package com.gurukul.schools;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Registers a throwaway school with a unique name and unique principal/admin phones. */
public final class SchoolTestSupport {

	private SchoolTestSupport() {
	}

	/** id of the school and a token for its ADMIN (the principal login registration creates). */
	public record RegisteredSchool(String id, String adminToken) {
	}

	public static RegisteredSchool register(MockMvc mockMvc, String namePrefix) throws Exception {
		String json = mockMvc.perform(registration(namePrefix + " " + randomDigits(6), "302001",
						"9" + randomDigits(9), "8" + randomDigits(9)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return new RegisteredSchool(JsonPath.read(json, "$.data.school.id"), JsonPath.read(json, "$.data.principal.token"));
	}

	public static MockHttpServletRequestBuilder registration(String name, String pincode, String principalPhone,
			String adminPhone) {
		return post("/api/v1/schools")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "%s", "address": "1 Test Street", "city": "Jaipur", "state": "Rajasthan",
						 "pincode": "%s", "contactEmail": "office@test.example", "contactPhone": "%s",
						 "principalName": "Dr. Test", "directorName": "Mr. Test",
						 "principalPhone": "%s", "adminPhone": "%s"}
						""".formatted(name, pincode, principalPhone, principalPhone, adminPhone));
	}

	public static String randomDigits(int count) {
		StringBuilder digits = new StringBuilder(count);
		for (int i = 0; i < count; i++) {
			digits.append(ThreadLocalRandom.current().nextInt(10));
		}
		return digits.toString();
	}

}
