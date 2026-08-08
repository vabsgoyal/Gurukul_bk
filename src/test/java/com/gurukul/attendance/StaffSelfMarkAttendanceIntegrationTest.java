package com.gurukul.attendance;

import com.gurukul.auth.AuthTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A fresh (just-registered) school starts with no location configured, so self-mark's
 * "location not configured" rejection can be tested without depending on whether some other
 * test class has already set a location on the shared seed school.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StaffSelfMarkAttendanceIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void selfMarkRejectsUntilLocationConfiguredThenAcceptsWithinRadiusAndRejectsOutside() throws Exception {
		MvcResult schoolResult = mockMvc.perform(post("/api/v1/schools")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Geofence Test School",
								  "address": "10 Test Street",
								  "city": "Jaipur",
								  "state": "Rajasthan",
								  "pincode": "302001",
								  "contactEmail": "office@geofencetest.example",
								  "contactPhone": "9111111111",
								  "principalName": "Dr. Test Principal",
								  "directorName": "Mr. Test Director",
								  "principalPhone": "9111111111",
								  "adminPhone": "8111111111"
								}
								"""))
				.andExpect(status().isOk())
				.andReturn();
		String schoolId = JsonPath.read(schoolResult.getResponse().getContentAsString(), "$.data.school.id");
		String adminBearer = "Bearer " + (String) JsonPath.read(schoolResult.getResponse().getContentAsString(), "$.data.principal.token");

		String teacherEmployeeId = AuthTestSupport.createEmployee(mockMvc, schoolId, "Geofenced Teacher");
		String teacherBearer = "Bearer " + AuthTestSupport.provisionAndLogin(
				mockMvc, schoolId, adminBearer.substring("Bearer ".length()), "employees", teacherEmployeeId, "TEACHER");

		mockMvc.perform(post("/api/v1/staff-attendance/self-mark")
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, teacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"latitude": 26.9124, "longitude": 75.7873}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("not been configured")));

		mockMvc.perform(put("/api/v1/schools/" + schoolId + "/location")
						.header(HttpHeaders.AUTHORIZATION, adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"latitude": 26.9124, "longitude": 75.7873, "geofenceRadiusMeters": 100}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.geofenceRadiusMeters").value(100));

		mockMvc.perform(post("/api/v1/staff-attendance/self-mark")
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, teacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"latitude": 26.9124, "longitude": 75.7873, "accuracy": 12.5}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PRESENT"))
				.andExpect(jsonPath("$.data.selfMarked").value(true));

		mockMvc.perform(post("/api/v1/staff-attendance/self-mark")
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, teacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"latitude": 27.9124, "longitude": 75.7873}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("away from the school")));
	}

}
