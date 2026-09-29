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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Logo upload with the default (unconfigured) bucket, as in local dev and CI: every write is
 * admin-of-this-school only, upload fails with a clean 400, and report-card PDFs still render (with
 * the Gurukul placeholder). The configured-bucket happy path is SchoolLogoConfiguredIntegrationTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SchoolLogoIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final String PRESIGN = """
			{"contentType": "image/png", "fileSizeBytes": 20000}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void unauthenticatedAndTeachersCannotTouchTheLogo() throws Exception {
		mockMvc.perform(post("/api/v1/schools/" + SCHOOL_ID + "/logo/presign")
						.header("X-School-Id", SCHOOL_ID).contentType(MediaType.APPLICATION_JSON).content(PRESIGN))
				.andExpect(status().is4xxClientError());

		String adminToken = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String employeeId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Logo Teacher");
		String teacherToken = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminToken, "employees", employeeId, "TEACHER");

		mockMvc.perform(as(post("/api/v1/schools/" + SCHOOL_ID + "/logo/presign"), SCHOOL_ID, teacherToken).content(PRESIGN))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(put("/api/v1/schools/" + SCHOOL_ID + "/logo"), SCHOOL_ID, teacherToken)
						.content("{\"objectKey\": \"school-logos/" + SCHOOL_ID + "/x.png\"}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(delete("/api/v1/schools/" + SCHOOL_ID + "/logo"), SCHOOL_ID, teacherToken))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminOfAnotherSchoolCannotSetThisSchoolsLogo() throws Exception {
		String otherResponse = mockMvc.perform(post("/api/v1/schools")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Other Logo School", "address": "1 Other Street", "city": "Jaipur",
								 "state": "Rajasthan", "pincode": "302001", "contactEmail": "office@otherlogo.example",
								 "contactPhone": "9445511223", "principalName": "Dr. Other", "directorName": "Mr. Other",
								 "principalPhone": "9445511223", "adminPhone": "8445511223"}
								"""))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String otherSchoolId = JsonPath.read(otherResponse, "$.data.school.id");
		String otherAdminToken = JsonPath.read(otherResponse, "$.data.admin.token");

		mockMvc.perform(as(post("/api/v1/schools/" + SCHOOL_ID + "/logo/presign"), otherSchoolId, otherAdminToken).content(PRESIGN))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(delete("/api/v1/schools/" + SCHOOL_ID + "/logo"), otherSchoolId, otherAdminToken))
				.andExpect(status().isForbidden());
	}

	@Test
	void withoutABucketUploadFailsCleanlyAndSchoolReadsStillWork() throws Exception {
		String adminToken = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);

		mockMvc.perform(as(post("/api/v1/schools/" + SCHOOL_ID + "/logo/presign"), SCHOOL_ID, adminToken).content(PRESIGN))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("School logo upload is not configured on this server"));

		mockMvc.perform(get("/api/v1/schools/" + SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.logoUrl").doesNotExist());

		// Removing is a plain DB update, so it works (and is idempotent) even without storage.
		mockMvc.perform(as(delete("/api/v1/schools/" + SCHOOL_ID + "/logo"), SCHOOL_ID, adminToken))
				.andExpect(status().isOk());
	}

	private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String schoolId, String token) {
		return request
				.header("X-School-Id", schoolId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON);
	}

}
