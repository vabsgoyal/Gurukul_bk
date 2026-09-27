package com.gurukul.auth;

import com.gurukul.auth.entity.Credential;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.repository.CredentialRepository;
import com.gurukul.auth.repository.RefreshTokenRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** OTP logins get a real, refreshable session; disabled logins can't use OTP at all. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingOtpChannel.class)
class OtpSessionIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final String CLASS_SECTION_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CapturingOtpChannel otpChannel;

	@Autowired
	private CredentialRepository credentialRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Test
	void singleProfileOtpLoginReturnsAWorkingRefreshToken() throws Exception {
		String phone = uniquePhone();
		AuthTestSupport.createEmployeeWithPhone(mockMvc, SCHOOL_ID, "OTP Session Teacher", phone);

		MvcResult verify = requestAndVerify(phone)
				.andExpect(jsonPath("$.data.profileSelectionRequired").value(false))
				.andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
				.andExpect(jsonPath("$.data.refreshTokenExpiresAt").isNotEmpty())
				.andReturn();
		String refreshToken = JsonPath.read(verify.getResponse().getContentAsString(), "$.data.refreshToken");

		mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"refreshToken\": \"" + refreshToken + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
	}

	@Test
	void multiProfileVerifyLeavesNoUnusedSessionAndSelectProfileGetsOne() throws Exception {
		String phone = uniquePhone();
		String first = createStudent(phone, "Aaa Sibling");
		createStudent(phone, "Bbb Sibling");
		long sessionsBefore = refreshTokenRepository.count();

		MvcResult verify = requestAndVerify(phone)
				.andExpect(jsonPath("$.data.profileSelectionRequired").value(true))
				.andExpect(jsonPath("$.data.token").isNotEmpty())
				.andExpect(jsonPath("$.data.refreshToken").doesNotExist())
				.andReturn();
		assertThat(refreshTokenRepository.count()).isEqualTo(sessionsBefore);

		String body = verify.getResponse().getContentAsString();
		String selectionToken = JsonPath.read(body, "$.data.selectionToken");
		mockMvc.perform(post("/api/v1/auth/otp/select-profile")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"selectionToken": "%s", "ownerType": "STUDENT", "ownerId": "%s"}
								""".formatted(selectionToken, first)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.ownerId").value(first))
				.andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
		assertThat(refreshTokenRepository.count()).isEqualTo(sessionsBefore + 1);
	}

	@Test
	void disabledCredentialCannotLogInByOtp() throws Exception {
		String phone = uniquePhone();
		String employeeId = AuthTestSupport.createEmployeeWithPhone(mockMvc, SCHOOL_ID, "OTP Disabled Teacher", phone);
		requestAndVerify(phone).andExpect(status().isOk());  // first login auto-provisions the credential
		setEnabled(OwnerType.EMPLOYEE, employeeId, false);

		mockMvc.perform(post("/api/v1/auth/otp/request")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"phone\": \"" + phone + "\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void disabledProfileIsLeftOutOfThePickerButOthersStillWork() throws Exception {
		String phone = uniquePhone();
		String disabled = createStudent(phone, "Aaa Disabled Sibling");
		String enabled = createStudent(phone, "Bbb Enabled Sibling");
		requestAndVerify(phone).andExpect(jsonPath("$.data.profileSelectionRequired").value(true));
		// Provision the first sibling's credential, then disable it.
		MvcResult picker = requestAndVerify(phone).andReturn();
		String selectionToken = JsonPath.read(picker.getResponse().getContentAsString(), "$.data.selectionToken");
		mockMvc.perform(post("/api/v1/auth/otp/select-profile")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"selectionToken": "%s", "ownerType": "STUDENT", "ownerId": "%s"}
								""".formatted(selectionToken, disabled)))
				.andExpect(status().isOk());
		setEnabled(OwnerType.STUDENT, disabled, false);

		requestAndVerify(phone)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.profileSelectionRequired").value(false))
				.andExpect(jsonPath("$.data.ownerId").value(enabled))
				.andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
	}

	private org.springframework.test.web.servlet.ResultActions requestAndVerify(String phone) throws Exception {
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

	private void setEnabled(OwnerType ownerType, String ownerId, boolean enabled) {
		Credential credential = credentialRepository.findByOwnerTypeAndOwnerId(ownerType, UUID.fromString(ownerId)).orElseThrow();
		credential.setEnabled(enabled);
		credentialRepository.save(credential);
	}

	private String createStudent(String phone, String name) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/students")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"rollNumber": "S-%s", "name": "%s", "dob": "2012-05-15", "gender": "MALE",
								 "address": "1 Test Road", "parentName": "Parent", "parentContact": "%s",
								 "classSectionId": "%s", "admissionDate": "2026-04-01"}
								""".formatted(UUID.randomUUID().toString().substring(0, 8), name, phone, CLASS_SECTION_B)))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
	}

	private static String uniquePhone() {
		return "7" + String.format("%09d", Math.abs(UUID.randomUUID().getMostSignificantBits()) % 1_000_000_000L);
	}

}
