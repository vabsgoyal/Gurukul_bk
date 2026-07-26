package com.gurukul.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OtpIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final String CLASS_SECTION_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void unregisteredPhoneIsRejected() throws Exception {
		mockMvc.perform(post("/api/v1/auth/otp/request")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"phone\": \"" + uniquePhone() + "\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void wrongOtpIsRejected() throws Exception {
		String phone = uniquePhone();
		createEmployeeWithPhone(phone);

		mockMvc.perform(post("/api/v1/auth/otp/verify")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"phone\": \"" + phone + "\", \"otp\": \"9999\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void teacherAutoProvisionsOnFirstOtpLogin() throws Exception {
		String phone = uniquePhone();
		String employeeId = createEmployeeWithPhone(phone);

		mockMvc.perform(post("/api/v1/auth/otp/request")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"phone\": \"" + phone + "\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/auth/otp/verify")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"phone\": \"" + phone + "\", \"otp\": \"1234\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.role").value("TEACHER"))
				.andExpect(jsonPath("$.data.ownerId").value(employeeId))
				.andExpect(jsonPath("$.data.token").exists());
	}

	@Test
	void studentAutoProvisionsViaParentContactOnFirstOtpLogin() throws Exception {
		String phone = uniquePhone();
		String rollNumber = "OTP-" + UUID.randomUUID().toString().substring(0, 8);

		MvcResult studentResult = mockMvc.perform(post("/api/v1/students")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "rollNumber": "%s",
								  "name": "OTP Student",
								  "dob": "2012-05-15",
								  "gender": "MALE",
								  "address": "123 MG Road",
								  "parentName": "Parent Name",
								  "parentContact": "%s",
								  "classSectionId": "%s",
								  "admissionDate": "2026-04-01"
								}
								""".formatted(rollNumber, phone, CLASS_SECTION_B)))
				.andExpect(status().isOk())
				.andReturn();
		String studentId = JsonPath.read(studentResult.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(post("/api/v1/auth/otp/verify")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"phone\": \"" + phone + "\", \"otp\": \"1234\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.role").value("STUDENT"))
				.andExpect(jsonPath("$.data.ownerId").value(studentId));
	}

	@Test
	void secondOtpLoginReusesTheSameAutoProvisionedCredential() throws Exception {
		String phone = uniquePhone();
		createEmployeeWithPhone(phone);

		MvcResult first = mockMvc.perform(post("/api/v1/auth/otp/verify")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"phone\": \"" + phone + "\", \"otp\": \"1234\"}"))
				.andExpect(status().isOk())
				.andReturn();
		String firstOwnerId = JsonPath.read(first.getResponse().getContentAsString(), "$.data.ownerId");

		MvcResult second = mockMvc.perform(post("/api/v1/auth/otp/verify")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"phone\": \"" + phone + "\", \"otp\": \"1234\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.role").value("TEACHER"))
				.andReturn();
		String secondOwnerId = JsonPath.read(second.getResponse().getContentAsString(), "$.data.ownerId");

		org.assertj.core.api.Assertions.assertThat(secondOwnerId).isEqualTo(firstOwnerId);
	}

	private String createEmployeeWithPhone(String phone) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/employees")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "OTP Teacher", "designation": "Teacher", "joinDate": "2024-04-01", "contactPhone": "%s"}
								""".formatted(phone)))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
	}

	private static String uniquePhone() {
		String digits = (UUID.randomUUID().toString() + UUID.randomUUID()).replaceAll("[^0-9]", "");
		return "9" + digits.substring(0, 9);
	}

}
