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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
						.header("X-School-Id", schoolId)
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

	@Test
	void selfMarkRefusesMockedAndStaleFixesAndLocationRejectsHugeRadius() throws Exception {
		MvcResult schoolResult = mockMvc.perform(post("/api/v1/schools")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Mock GPS Test School",
								  "address": "11 Test Street",
								  "city": "Jaipur",
								  "state": "Rajasthan",
								  "pincode": "302001",
								  "contactEmail": "office@mockgpstest.example",
								  "contactPhone": "9111111122",
								  "principalName": "Dr. Mock Principal",
								  "directorName": "Mr. Mock Director",
								  "principalPhone": "9111111122",
								  "adminPhone": "8111111122"
								}
								"""))
				.andExpect(status().isOk())
				.andReturn();
		String schoolId = JsonPath.read(schoolResult.getResponse().getContentAsString(), "$.data.school.id");
		String adminBearer = "Bearer " + (String) JsonPath.read(schoolResult.getResponse().getContentAsString(), "$.data.principal.token");

		mockMvc.perform(put("/api/v1/schools/" + schoolId + "/location")
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"latitude": 26.9124, "longitude": 75.7873, "geofenceRadiusMeters": 50000}
								"""))
				.andExpect(status().isBadRequest());

		mockMvc.perform(put("/api/v1/schools/" + schoolId + "/location")
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"latitude": 26.9124, "longitude": 75.7873, "geofenceRadiusMeters": 100}
								"""))
				.andExpect(status().isOk());

		String teacherEmployeeId = AuthTestSupport.createEmployee(mockMvc, schoolId, "Mock GPS Teacher");
		String teacherBearer = "Bearer " + AuthTestSupport.provisionAndLogin(
				mockMvc, schoolId, adminBearer.substring("Bearer ".length()), "employees", teacherEmployeeId, "TEACHER");

		mockMvc.perform(post("/api/v1/staff-attendance/self-mark")
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, teacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"latitude": 26.9124, "longitude": 75.7873, "mocked": true}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("fake (mock) location")));

		long stale = System.currentTimeMillis() - 10 * 60 * 1000L;
		mockMvc.perform(post("/api/v1/staff-attendance/self-mark")
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, teacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"latitude\": 26.9124, \"longitude\": 75.7873, \"fixTimestamp\": " + stale + "}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("out of date")));

		mockMvc.perform(post("/api/v1/staff-attendance/self-mark")
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, teacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"latitude\": 26.9124, \"longitude\": 75.7873, \"mocked\": false, \"fixTimestamp\": "
								+ System.currentTimeMillis() + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PRESENT"));
	}


	/**
	 * Admin-entered attendance (bulk staff entry) wins over self check-in: a teacher marked ABSENT
	 * by an admin cannot replace it with PRESENT. Re-saving over their own self-mark stays allowed.
	 */
	@Test
	void selfMarkDoesNotOverwriteAdminEnteredAttendance() throws Exception {
		MvcResult schoolResult = mockMvc.perform(post("/api/v1/schools")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Admin Wins Test School",
								  "address": "12 Test Street",
								  "city": "Jaipur",
								  "state": "Rajasthan",
								  "pincode": "302001",
								  "contactEmail": "office@adminwinstest.example",
								  "contactPhone": "9111111133",
								  "principalName": "Dr. Admin Principal",
								  "directorName": "Mr. Admin Director",
								  "principalPhone": "9111111133",
								  "adminPhone": "8111111133"
								}
								"""))
				.andExpect(status().isOk())
				.andReturn();
		String schoolId = JsonPath.read(schoolResult.getResponse().getContentAsString(), "$.data.school.id");
		String adminBearer = "Bearer " + (String) JsonPath.read(schoolResult.getResponse().getContentAsString(), "$.data.principal.token");

		mockMvc.perform(put("/api/v1/schools/" + schoolId + "/location")
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"latitude": 26.9124, "longitude": 75.7873, "geofenceRadiusMeters": 100}
								"""))
				.andExpect(status().isOk());

		String absentTeacherId = AuthTestSupport.createEmployee(mockMvc, schoolId, "Admin Marked Teacher");
		String absentTeacherBearer = "Bearer " + AuthTestSupport.provisionAndLogin(
				mockMvc, schoolId, adminBearer.substring("Bearer ".length()), "employees", absentTeacherId, "TEACHER");
		String selfTeacherId = AuthTestSupport.createEmployee(mockMvc, schoolId, "Self Marked Teacher");
		String selfTeacherBearer = "Bearer " + AuthTestSupport.provisionAndLogin(
				mockMvc, schoolId, adminBearer.substring("Bearer ".length()), "employees", selfTeacherId, "TEACHER");

		String today = java.time.LocalDate.now().toString();
		mockMvc.perform(post("/api/v1/staff-attendance")
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"date": "%s", "records": [{"employeeId": "%s", "status": "ABSENT"}]}
								""".formatted(today, absentTeacherId)))
				.andExpect(status().isOk());

		String fix = "{\"latitude\": 26.9124, \"longitude\": 75.7873}";
		mockMvc.perform(post("/api/v1/staff-attendance/self-mark")
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, absentTeacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(fix))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(containsString("already marked your attendance today as ABSENT")));

		mockMvc.perform(get("/api/v1/staff-attendance")
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, adminBearer)
						.param("date", today))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.entries[?(@.employeeId == '" + absentTeacherId + "')].status").value("ABSENT"));

		// Checking in twice over one's own self-mark is still fine.
		for (int i = 0; i < 2; i++) {
			mockMvc.perform(post("/api/v1/staff-attendance/self-mark")
							.header("X-School-Id", schoolId)
							.header(HttpHeaders.AUTHORIZATION, selfTeacherBearer)
							.contentType(MediaType.APPLICATION_JSON)
							.content(fix))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status").value("PRESENT"));
		}
	}

}
