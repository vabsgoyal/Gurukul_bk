package com.gurukul.schools;

import com.gurukul.auth.AuthTestSupport;
import com.gurukul.parents.ParentTestSupport;
import com.gurukul.schools.SchoolTestSupport.RegisteredSchool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/v1/schools/{id} carries the fee-receiving bank account and UPI override: only this
 * school's ADMIN sees those; this school's other users see the rest of the profile; nobody else
 * sees anything.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SchoolProfileVisibilityIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private RegisteredSchool school;

	@BeforeEach
	void setUp() throws Exception {
		school = SchoolTestSupport.register(mockMvc, "Visibility School");
		mockMvc.perform(put("/api/v1/schools/" + school.id())
						.header("X-School-Id", school.id())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + school.adminToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Visibility School", "address": "1 Test Street", "city": "Jaipur",
								 "state": "Rajasthan", "pincode": "302001", "contactEmail": "office@test.example",
								 "contactPhone": "9000011111", "principalName": "Dr. Test", "directorName": "Mr. Test",
								 "bankAccountNumber": "123456789012", "bankIfsc": "SBIN0000001",
								 "bankAccountHolderName": "Visibility School", "upiVpaOverride": "school@upi"}
								"""))
				.andExpect(status().isOk());
		mockMvc.perform(put("/api/v1/schools/" + school.id() + "/location")
						.header("X-School-Id", school.id())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + school.adminToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"latitude": 26.9124, "longitude": 75.7873, "geofenceRadiusMeters": 150}
								"""))
				.andExpect(status().isOk());
	}

	@Test
	void adminSeesBankAndUpiDetails() throws Exception {
		view(school.id(), school.adminToken())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.bankAccountNumber").value("123456789012"))
				.andExpect(jsonPath("$.data.bankIfsc").value("SBIN0000001"))
				.andExpect(jsonPath("$.data.upiVpaOverride").value("school@upi"))
				.andExpect(jsonPath("$.data.latitude").value(26.9124));
	}

	@Test
	void teacherSeesProfileButNotBankDetails() throws Exception {
		String teacherId = AuthTestSupport.createEmployee(mockMvc, school.id(), "Visibility Teacher");
		String teacherToken = AuthTestSupport.provisionAndLogin(
				mockMvc, school.id(), school.adminToken(), "employees", teacherId, "TEACHER");

		assertProfileWithoutBank(view(school.id(), teacherToken));
	}

	@Test
	void studentSeesProfileButNotBankDetails() throws Exception {
		String sectionId = ParentTestSupport.createSection(mockMvc, school.id(), "5", "A");
		String studentId = AuthTestSupport.createStudent(mockMvc, school.id(), sectionId, "Visibility Student");
		String studentToken = AuthTestSupport.provisionAndLogin(
				mockMvc, school.id(), school.adminToken(), "students", studentId, "STUDENT");

		assertProfileWithoutBank(view(school.id(), studentToken));
	}

	@Test
	void anonymousCallerGetsNothing() throws Exception {
		mockMvc.perform(get("/api/v1/schools/" + school.id())
						.header("X-School-Id", school.id())
						.header(HttpHeaders.AUTHORIZATION, ""))
				.andExpect(status().is4xxClientError())
				.andExpect(jsonPath("$.data.bankAccountNumber").doesNotExist());
	}

	@Test
	void adminOfAnotherSchoolGetsNothing() throws Exception {
		RegisteredSchool other = SchoolTestSupport.register(mockMvc, "Visibility Other School");

		mockMvc.perform(get("/api/v1/schools/" + school.id())
						.header("X-School-Id", other.id())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + other.adminToken()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.data.bankAccountNumber").doesNotExist());
	}

	private ResultActions view(String schoolId, String token) throws Exception {
		return mockMvc.perform(get("/api/v1/schools/" + schoolId)
				.header("X-School-Id", schoolId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private static void assertProfileWithoutBank(ResultActions result) throws Exception {
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("Visibility School"))
				.andExpect(jsonPath("$.data.contactPhone").value("9000011111"))
				.andExpect(jsonPath("$.data.latitude").value(26.9124))
				.andExpect(jsonPath("$.data.geofenceRadiusMeters").value(150))
				.andExpect(jsonPath("$.data.bankAccountNumber").value(nullValue()))
				.andExpect(jsonPath("$.data.bankIfsc").value(nullValue()))
				.andExpect(jsonPath("$.data.bankAccountHolderName").value(nullValue()))
				.andExpect(jsonPath("$.data.upiVpaOverride").value(nullValue()));
	}

}
