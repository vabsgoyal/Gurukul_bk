package com.gurukul.schools;

import com.gurukul.auth.AuthTestSupport;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PUT /api/v1/schools/{id} carries the bank account/IFSC/UPI VPA fee payments are routed to, and was
 * previously callable with no authentication at all.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SchoolUpdateAuthorizationIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	private static final String HIJACK_PAYLOAD = """
			{"name": "Hijacked School", "address": "1 Evil Street", "city": "Jaipur", "state": "Rajasthan",
			 "pincode": "302001", "contactEmail": "evil@example.com", "contactPhone": "9000000000",
			 "principalName": "Evil", "directorName": "Evil", "bankAccountNumber": "999999999999",
			 "bankIfsc": "EVIL0000001", "upiVpaOverride": "attacker@upi"}
			""";

	private static final String LOCATION_PAYLOAD = """
			{"latitude": 1.0, "longitude": 1.0, "geofenceRadiusMeters": 5000}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void unauthenticatedCannotEditSchoolProfile() throws Exception {
		mockMvc.perform(put("/api/v1/schools/" + SCHOOL_ID)
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(HIJACK_PAYLOAD))
				.andExpect(status().is4xxClientError());

		assertNameUnchanged();
	}

	@Test
	void teacherCannotEditSchoolProfile() throws Exception {
		String adminToken = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String employeeId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "School Edit Teacher");
		String teacherToken = AuthTestSupport.provisionAndLogin(
				mockMvc, SCHOOL_ID, adminToken, "employees", employeeId, "TEACHER");

		mockMvc.perform(asUser(put("/api/v1/schools/" + SCHOOL_ID), SCHOOL_ID, teacherToken).content(HIJACK_PAYLOAD))
				.andExpect(status().isForbidden());

		assertNameUnchanged();
	}

	@Test
	void adminOfAnotherSchoolCannotEditThisSchool() throws Exception {
		String otherResponse = mockMvc.perform(post("/api/v1/schools")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Other Authz School", "address": "1 Other Street", "city": "Jaipur",
								 "state": "Rajasthan", "pincode": "302001", "contactEmail": "office@otherauthz.example",
								 "contactPhone": "9445566778", "principalName": "Dr. Other", "directorName": "Mr. Other",
								 "principalPhone": "9445566778", "adminPhone": "8445566778"}
								"""))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String otherSchoolId = JsonPath.read(otherResponse, "$.data.school.id");
		String otherAdminToken = JsonPath.read(otherResponse, "$.data.principal.token");

		// Token matches its own X-School-Id header, but the path targets this school.
		mockMvc.perform(asUser(put("/api/v1/schools/" + SCHOOL_ID), otherSchoolId, otherAdminToken).content(HIJACK_PAYLOAD))
				.andExpect(status().isForbidden());
		mockMvc.perform(asUser(put("/api/v1/schools/" + SCHOOL_ID + "/location"), otherSchoolId, otherAdminToken)
						.content(LOCATION_PAYLOAD))
				.andExpect(status().isForbidden());

		assertNameUnchanged();
	}

	@Test
	void ownAdminCanEditOwnSchool() throws Exception {
		String adminToken = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String current = mockMvc.perform(get("/api/v1/schools/" + SCHOOL_ID))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String name = JsonPath.read(current, "$.data.name");

		mockMvc.perform(asUser(put("/api/v1/schools/" + SCHOOL_ID), SCHOOL_ID, adminToken)
						.content("""
								{"name": "%s", "address": "123 Education Lane", "city": "Jaipur",
								 "state": "Rajasthan", "pincode": "302001", "contactEmail": "admin@gurukul.demo",
								 "contactPhone": "9876543210", "principalName": "Dr. Meena Sharma",
								 "directorName": "Mr. Rajesh Kumar"}
								""".formatted(name)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value(name));
	}

	private static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request, String schoolId, String token) {
		return request
				.header("X-School-Id", schoolId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON);
	}

	private void assertNameUnchanged() throws Exception {
		mockMvc.perform(get("/api/v1/schools/" + SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value(org.hamcrest.Matchers.not("Hijacked School")));
	}

}
