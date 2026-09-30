package com.gurukul.auth;

import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.entity.EmployeeStatus;
import com.gurukul.employees.repository.EmployeeRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Former staff kept on file as INACTIVE can't log in by any route. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingOtpChannel.class)
class InactiveStaffLoginIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final String DISABLED = "This account has been disabled - please contact your school";

	@Autowired private MockMvc mockMvc;
	@Autowired private EmployeeRepository employeeRepository;

	@Test
	void aFormerTeacherCannotGetAnOtp() throws Exception {
		String phone = uniquePhone();
		createEmployee("Former Teacher", phone, "INACTIVE");

		requestOtp(phone)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value(DISABLED));
	}

	@Test
	void aTeacherMadeInactiveLosesPasswordLoginAndTheirSession() throws Exception {
		String employeeId = createEmployee("Leaving Teacher", uniquePhone(), "ACTIVE");
		String admin = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String username = "leaving-" + UUID.randomUUID().toString().substring(0, 8);
		mockMvc.perform(post("/api/v1/employees/" + employeeId + "/credentials")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\": \"" + username + "\", \"password\": \"Password@123\", \"role\": \"TEACHER\"}"))
				.andExpect(status().isOk());
		String refreshToken = JsonPath.read(login(username).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(), "$.data.refreshToken");

		Employee employee = employeeRepository.findById(UUID.fromString(employeeId)).orElseThrow();
		employee.setStatus(EmployeeStatus.INACTIVE);
		employeeRepository.save(employee);

		login(username).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(DISABLED));
		mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
						.content("{\"refreshToken\": \"" + refreshToken + "\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void aChildOnAFormerTeachersPhoneCanStillLogIn() throws Exception {
		String phone = uniquePhone();
		createEmployee("Former Teacher Parent", phone, "INACTIVE");
		String childId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "Child");
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/students/" + childId)
				.header("X-School-Id", SCHOOL_ID).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Child", "dob": "2014-01-01", "gender": "MALE", "address": "1 Road", "parentName": "Parent",
						 "parentContact": "%s", "classSectionId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "admissionDate": "2026-04-01"}
						""".formatted(phone))).andExpect(status().isOk());

		requestOtp(phone).andExpect(status().isOk());
	}

	private ResultActions requestOtp(String phone) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/otp/request")
				.header("X-School-Id", SCHOOL_ID)
				.header(HttpHeaders.AUTHORIZATION, "")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"phone\": \"" + phone + "\"}"));
	}

	private ResultActions login(String username) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
				.header("X-School-Id", SCHOOL_ID)
				.header(HttpHeaders.AUTHORIZATION, "")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\": \"" + username + "\", \"password\": \"Password@123\"}"));
	}

	private String createEmployee(String name, String phone, String status) throws Exception {
		String response = mockMvc.perform(post("/api/v1/employees")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "%s", "designation": "Teacher", "joinDate": "2020-04-01", "contactPhone": "%s",
								 "status": "%s", "employeeType": "TEACHING"}
								""".formatted(name, phone, status)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.data.id");
	}

	private static String uniquePhone() {
		String digits = (UUID.randomUUID().toString() + UUID.randomUUID()).replaceAll("[^0-9]", "");
		return "8" + digits.substring(0, 9);
	}

}
