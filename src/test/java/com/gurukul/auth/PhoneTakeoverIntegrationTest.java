package com.gurukul.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OTP login finds staff by contactPhone and students by parentContact, and signs in as that
 * record. So only an admin may change those numbers, and only an admin
 * may put a phone on a new staff record - otherwise any login could take over another account.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingOtpChannel.class)
class PhoneTakeoverIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final String SECTION = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

	@Autowired private MockMvc mockMvc;

	private String admin;
	private String victimAdminId;
	private String teacherId;
	private String teacher;
	private String studentId;
	private String student;
	private String attackerPhone;

	@BeforeEach
	void setUp() throws Exception {
		admin = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		victimAdminId = createEmployee(admin, "Victim Principal", phone()).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		victimAdminId = JsonPath.read(victimAdminId, "$.data.id");
		AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, admin, "employees", victimAdminId, "ADMIN");

		teacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Takeover Teacher");
		teacher = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, admin, "employees", teacherId, "TEACHER");
		studentId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, SECTION, "Takeover Student");
		student = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, admin, "students", studentId, "STUDENT");
		attackerPhone = phone();
	}

	@Test
	void aNonAdminCannotPutTheirPhoneOnAnAdminsRecord() throws Exception {
		for (String attacker : new String[] {teacher, student}) {
			updateEmployee(attacker, victimAdminId, "Victim Principal", attackerPhone).andExpect(status().isForbidden());
		}
		// The number never reached the record, so it can't be used to log in.
		requestOtp(attackerPhone).andExpect(status().isNotFound());
	}

	@Test
	void aNonAdminCannotGiveThemselvesANewStaffLogin() throws Exception {
		createEmployee(student, "Fake Teacher", attackerPhone).andExpect(status().isForbidden());
		requestOtp(attackerPhone).andExpect(status().isNotFound());
		// Adding a colleague by name only (the EmployeePicker) still works.
		createEmployee(teacher, "New Colleague", null).andExpect(status().isOk());
	}

	@Test
	void aStudentCannotTakeOverAnotherStudent() throws Exception {
		String classmateId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, SECTION, "Classmate");
		updateStudent(student, classmateId, "Classmate", attackerPhone).andExpect(status().isForbidden());
		updateStudent(teacher, classmateId, "Classmate", attackerPhone).andExpect(status().isForbidden());
		requestOtp(attackerPhone).andExpect(status().isNotFound());
	}

	@Test
	void onlyAnAdminEditsAWholeRecordEvenTheirOwn() throws Exception {
		// A whole-record PUT also carries class-section, status and bank account, so it's admin-only
		// (SecurityConfig); own-profile edits in the app go through /api/v1/id-cards/*/profile.
		updateEmployee(teacher, teacherId, "Takeover Teacher", phone()).andExpect(status().isForbidden());
		updateStudent(student, studentId, "Takeover Student", phone()).andExpect(status().isForbidden());

		String newTeacherPhone = phone();
		updateEmployee(admin, teacherId, "Takeover Teacher", newTeacherPhone).andExpect(status().isOk());
		updateStudent(admin, studentId, "Takeover Student", phone()).andExpect(status().isOk());
		updateEmployee(admin, victimAdminId, "Victim Principal Renamed", phone()).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/employees/" + teacherId).header("X-School-Id", SCHOOL_ID))
				.andExpect(jsonPath("$.data.contactPhone").value(newTeacherPhone));
	}

	private ResultActions createEmployee(String bearer, String name, String phone) throws Exception {
		return mockMvc.perform(post("/api/v1/employees")
				.header("X-School-Id", SCHOOL_ID)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "%s", "designation": "Teacher", "joinDate": "2024-04-01"%s}
						""".formatted(name, phone == null ? "" : ", \"contactPhone\": \"" + phone + "\"")));
	}

	private ResultActions updateEmployee(String bearer, String id, String name, String phone) throws Exception {
		return mockMvc.perform(put("/api/v1/employees/" + id)
				.header("X-School-Id", SCHOOL_ID)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "%s", "designation": "Teacher", "joinDate": "2024-04-01", "contactPhone": "%s"}
						""".formatted(name, phone)));
	}

	private ResultActions updateStudent(String bearer, String id, String name, String parentContact) throws Exception {
		return mockMvc.perform(put("/api/v1/students/" + id)
				.header("X-School-Id", SCHOOL_ID)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "%s", "dob": "2012-05-15", "gender": "MALE", "address": "1 Road", "parentName": "Parent",
						 "parentContact": "%s", "classSectionId": "%s", "admissionDate": "2026-04-01"}
						""".formatted(name, parentContact, SECTION)));
	}

	private ResultActions requestOtp(String phone) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/otp/request")
				.header("X-School-Id", SCHOOL_ID)
				.header(HttpHeaders.AUTHORIZATION, "")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"phone\": \"" + phone + "\"}"));
	}

	private static String phone() {
		String digits = (UUID.randomUUID().toString() + UUID.randomUUID()).replaceAll("[^0-9]", "");
		return "7" + digits.substring(0, 9);
	}

}
