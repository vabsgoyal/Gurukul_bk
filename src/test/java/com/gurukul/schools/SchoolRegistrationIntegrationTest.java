package com.gurukul.schools;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SchoolRegistrationIntegrationTest {

	private static final String SEED_SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	private static String schoolRegistrationJson(String name, String address, String email, String phone) {
		return """
				{
				  "name": "%s",
				  "address": "%s",
				  "city": "Jaipur",
				  "state": "Rajasthan",
				  "pincode": "302001",
				  "contactEmail": "%s",
				  "contactPhone": "%s",
				  "principalName": "Dr. Test Principal",
				  "directorName": "Mr. Test Director"
				}
				""".formatted(name, address, email, phone);
	}

	@Test
	void registerSchoolWithoutHeader() throws Exception {
		mockMvc.perform(post("/api/v1/schools")
						.contentType(MediaType.APPLICATION_JSON)
						.content(schoolRegistrationJson(
								"New Public School", "10 Main Street", "office@nps.example", "9123456789")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.name").value("New Public School"))
				.andExpect(jsonPath("$.data.studentCount").value(0))
				.andExpect(jsonPath("$.data.classSectionCount").value(0))
				.andExpect(jsonPath("$.data.teacherCount").value(0))
				.andExpect(jsonPath("$.message").value("School registered"));
	}

	@Test
	void getSchoolByIdWithoutHeader() throws Exception {
		mockMvc.perform(get("/api/v1/schools/" + SEED_SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("Gurukul Demo School"))
				.andExpect(jsonPath("$.data.principalName").value("Dr. Meena Sharma"))
				.andExpect(jsonPath("$.data.directorName").value("Mr. Rajesh Kumar"))
				.andExpect(jsonPath("$.data.classSectionCount").value(greaterThanOrEqualTo(2)))
				.andExpect(jsonPath("$.data.teacherCount").value(0));
	}

	@Test
	void invalidSchoolIdHeaderReturns400() throws Exception {
		mockMvc.perform(get("/api/v1/students").header("X-School-Id", "22222222-2222-2222-2222-222222222222"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("School not found"));
	}

	@Test
	void registerThenUseSchoolIdHeader() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/schools")
						.contentType(MediaType.APPLICATION_JSON)
						.content(schoolRegistrationJson(
								"Tenant Test School", "99 Test Road", "test@school.example", "9000000001")))
				.andExpect(status().isOk())
				.andReturn();

		String schoolId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(get("/api/v1/class-sections").header("X-School-Id", schoolId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(0));
	}

}
