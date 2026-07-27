package com.gurukul.auth;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

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

	public static String createEmployee(MockMvc mockMvc, String schoolId, String name) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/employees")
						.header("X-School-Id", schoolId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "%s", "designation": "Teacher", "joinDate": "2024-04-01"}
								""".formatted(name)))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
	}

	public static String createStudent(MockMvc mockMvc, String schoolId, String classSectionId, String name) throws Exception {
		String rollNumber = "T-" + UUID.randomUUID().toString().substring(0, 8);
		MvcResult result = mockMvc.perform(post("/api/v1/students")
						.header("X-School-Id", schoolId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "rollNumber": "%s",
								  "name": "%s",
								  "dob": "2012-05-15",
								  "gender": "MALE",
								  "address": "123 MG Road",
								  "parentName": "Parent of %s",
								  "parentContact": "9%09d",
								  "classSectionId": "%s",
								  "admissionDate": "2026-04-01"
								}
								""".formatted(rollNumber, name, name, Math.abs(rollNumber.hashCode()) % 1_000_000_000, classSectionId)))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
	}

	/** Provisions a credential (as admin) for an already-created employee/student and logs in as them. */
	public static String provisionAndLogin(
			MockMvc mockMvc, String schoolId, String adminBearer, String ownerKind, String ownerId, String role)
			throws Exception {
		String username = "u-" + UUID.randomUUID().toString().substring(0, 12);
		String password = "Password@123";
		mockMvc.perform(post("/api/v1/" + ownerKind + "/" + ownerId + "/credentials")
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username": "%s", "password": "%s", "role": "%s"}
								""".formatted(username, password, role)))
				.andExpect(status().isOk());
		return login(mockMvc, schoolId, username, password);
	}

}
