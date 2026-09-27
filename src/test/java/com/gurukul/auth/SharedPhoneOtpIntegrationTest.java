package com.gurukul.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Siblings (and teacher-parents) sharing one phone number log in through a profile picker. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingOtpChannel.class)
class SharedPhoneOtpIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final String CLASS_SECTION_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CapturingOtpChannel otpChannel;

	@Test
	void siblingsOnOnePhoneEachGetTheirOwnLogin() throws Exception {
		String phone = uniquePhone();
		String zara = createStudent("Zara Sibling", phone);
		String aarav = createStudent("Aarav Sibling", phone);

		MvcResult verified = verify(phone)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.profileSelectionRequired").value(true))
				// Old app versions without the picker still get a login, for the first profile.
				.andExpect(jsonPath("$.data.ownerId").value(aarav))
				.andExpect(jsonPath("$.data.token").exists())
				.andExpect(jsonPath("$.data.profiles.length()").value(2))
				// Sorted by name, so the picker order is stable.
				.andExpect(jsonPath("$.data.profiles[0].ownerId").value(aarav))
				.andExpect(jsonPath("$.data.profiles[0].className").exists())
				.andExpect(jsonPath("$.data.profiles[1].ownerId").value(zara))
				.andReturn();
		String selectionToken = read(verified, "$.data.selectionToken");

		// Both siblings can log in off one OTP - the second used to fail on a duplicate username.
		selectProfile(selectionToken, "STUDENT", aarav)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.ownerId").value(aarav))
				.andExpect(jsonPath("$.data.username").value(phone));
		selectProfile(selectionToken, "STUDENT", zara)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.ownerId").value(zara))
				.andExpect(jsonPath("$.data.username").value(phone + "-" + zara.substring(0, 8)));

		// A later login reuses each sibling's credential.
		String again = read(verify(phone).andExpect(status().isOk()).andReturn(), "$.data.selectionToken");
		selectProfile(again, "STUDENT", zara)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.username").value(phone + "-" + zara.substring(0, 8)));
	}

	@Test
	void selectionTokenCannotPickAnUnrelatedProfileOrActAsAnAccessToken() throws Exception {
		String phone = uniquePhone();
		createStudent("Sibling One", phone);
		createStudent("Sibling Two", phone);
		String stranger = createStudent("Stranger", uniquePhone());
		String selectionToken = read(verify(phone).andReturn(), "$.data.selectionToken");

		selectProfile(selectionToken, "STUDENT", stranger).andExpect(status().isForbidden());
		selectProfile("not-a-token", "STUDENT", stranger).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/auth/profiles")
						.header("X-School-Id", SCHOOL_ID)
						.header("Authorization", "Bearer " + selectionToken))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void studentCanSwitchToASiblingWithoutANewOtp() throws Exception {
		String phone = uniquePhone();
		String first = createStudent("Switch A", phone);
		String second = createStudent("Switch B", phone);
		String stranger = createStudent("Switch Stranger", uniquePhone());
		String selectionToken = read(verify(phone).andReturn(), "$.data.selectionToken");
		String token = read(selectProfile(selectionToken, "STUDENT", first).andReturn(), "$.data.token");

		MvcResult listed = mockMvc.perform(get("/api/v1/auth/profiles")
						.header("X-School-Id", SCHOOL_ID)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(2))
				.andReturn();
		List<Boolean> current = JsonPath.read(listed.getResponse().getContentAsString(), "$.data[*].current");
		assertThat(current).containsExactly(true, false);

		switchProfile(token, "STUDENT", second)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.ownerId").value(second))
				.andExpect(jsonPath("$.data.token").exists());
		switchProfile(token, "STUDENT", stranger).andExpect(status().isForbidden());
	}

	@Test
	void aChildsLoginNeverReachesTheParentsStaffProfile() throws Exception {
		String phone = uniquePhone();
		String teacher = createEmployee("Teacher Parent", phone);
		String child = createStudent("Teacher Child", phone);
		String selectionToken = read(verify(phone)
				.andExpect(jsonPath("$.data.profiles[0].ownerType").value("EMPLOYEE"))
				.andExpect(jsonPath("$.data.profiles[0].role").value("TEACHER"))
				.andReturn(), "$.data.selectionToken");

		String childToken = read(selectProfile(selectionToken, "STUDENT", child).andReturn(), "$.data.token");
		mockMvc.perform(get("/api/v1/auth/profiles")
						.header("X-School-Id", SCHOOL_ID)
						.header("Authorization", "Bearer " + childToken))
				.andExpect(jsonPath("$.data.length()").value(1));
		switchProfile(childToken, "EMPLOYEE", teacher).andExpect(status().isForbidden());

		String teacherToken = read(selectProfile(selectionToken, "EMPLOYEE", teacher).andReturn(), "$.data.token");
		switchProfile(teacherToken, "STUDENT", child)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.ownerId").value(child));
	}

	@Test
	void profileEndpointsNeedALogin() throws Exception {
		mockMvc.perform(get("/api/v1/auth/profiles").header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isUnauthorized());
	}

	private org.springframework.test.web.servlet.ResultActions verify(String phone) throws Exception {
		mockMvc.perform(post("/api/v1/auth/otp/request")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"phone\": \"" + phone + "\"}"))
				.andExpect(status().isOk());
		return mockMvc.perform(post("/api/v1/auth/otp/verify")
				.header("X-School-Id", SCHOOL_ID)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"phone\": \"" + phone + "\", \"otp\": \"" + otpChannel.lastCodeFor(phone) + "\"}"));
	}

	private org.springframework.test.web.servlet.ResultActions selectProfile(
			String selectionToken, String ownerType, String ownerId) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/otp/select-profile")
				.header("X-School-Id", SCHOOL_ID)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"selectionToken": "%s", "ownerType": "%s", "ownerId": "%s"}
						""".formatted(selectionToken, ownerType, ownerId)));
	}

	private org.springframework.test.web.servlet.ResultActions switchProfile(
			String token, String ownerType, String ownerId) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/profiles/switch")
				.header("X-School-Id", SCHOOL_ID)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ownerType": "%s", "ownerId": "%s"}
						""".formatted(ownerType, ownerId)));
	}

	private String createStudent(String name, String parentContact) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/students")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "rollNumber": "SIB-%s",
								  "name": "%s",
								  "dob": "2012-05-15",
								  "gender": "MALE",
								  "address": "123 MG Road",
								  "parentName": "Parent Name",
								  "parentContact": "%s",
								  "classSectionId": "%s",
								  "admissionDate": "2026-04-01"
								}
								""".formatted(UUID.randomUUID().toString().substring(0, 8), name, parentContact, CLASS_SECTION_B)))
				.andExpect(status().isOk())
				.andReturn();
		return read(result, "$.data.id");
	}

	private String createEmployee(String name, String phone) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/employees")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "%s", "designation": "Teacher", "joinDate": "2024-04-01", "contactPhone": "%s"}
								""".formatted(name, phone)))
				.andExpect(status().isOk())
				.andReturn();
		return read(result, "$.data.id");
	}

	private static String read(MvcResult result, String path) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), path);
	}

	private static String uniquePhone() {
		String digits = (UUID.randomUUID().toString() + UUID.randomUUID()).replaceAll("[^0-9]", "");
		return "9" + digits.substring(0, 9);
	}

}
