package com.gurukul.auth;

import com.gurukul.auth.config.PrincipalPhoneBackfillSeeder;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.repository.CredentialRepository;
import com.gurukul.schools.entity.School;
import com.gurukul.schools.repository.SchoolRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OTP can't be brute-forced (6 digits, burned after 5 wrong guesses, a few codes an hour per phone),
 * and the shared 9999999999 Principal login only exists for a school with no admin of its own.
 * The request limits are relaxed for the rest of the suite (pom.xml), so they're set back here.
 */
@SpringBootTest(properties = {"app.otp.request-cooldown-seconds=30", "app.otp.max-requests-per-hour=5"})
@AutoConfigureMockMvc
@Import(CapturingOtpChannel.class)
class OtpHardeningIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired private MockMvc mockMvc;
	@Autowired private CapturingOtpChannel otpChannel;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private SchoolRepository schoolRepository;
	@Autowired private CredentialRepository credentialRepository;
	@Autowired private PrincipalPhoneBackfillSeeder principalSeeder;

	@Test
	void codesAreSixDigitsAndBurnedAfterFiveWrongGuesses() throws Exception {
		String phone = teacherPhone();
		requestOtp(phone).andExpect(status().isOk());
		String code = otpChannel.lastCodeFor(phone);
		assertThat(code).hasSize(6);
		String wrong = code.equals("000000") ? "111111" : "000000";

		for (int i = 0; i < 4; i++) {
			verify(phone, wrong).andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.message").value("Invalid or expired OTP"));
		}
		verify(phone, wrong).andExpect(jsonPath("$.message").value("Too many wrong codes - please request a new one"));
		// Even the right code no longer works.
		verify(phone, code).andExpect(status().isUnauthorized());
	}

	@Test
	void theRightCodeStillWorksAfterAFewTypos() throws Exception {
		String phone = teacherPhone();
		requestOtp(phone).andExpect(status().isOk());
		String code = otpChannel.lastCodeFor(phone);
		String wrong = code.equals("000000") ? "111111" : "000000";
		for (int i = 0; i < 4; i++) {
			verify(phone, wrong).andExpect(status().isUnauthorized());
		}
		verify(phone, code).andExpect(status().isOk()).andExpect(jsonPath("$.data.token").exists());
	}

	@Test
	void aPhoneGetsOneCodePerCooldownAndFiveAnHour() throws Exception {
		String phone = teacherPhone();
		requestOtp(phone).andExpect(status().isOk());
		requestOtp(phone).andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.message").value("Please wait a moment before requesting another code"));

		for (int i = 0; i < 4; i++) {
			ageCodes(phone);
			requestOtp(phone).andExpect(status().isOk());
		}
		ageCodes(phone);
		requestOtp(phone).andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.message").value("Too many codes requested for this number - please try again in an hour"));
	}

	@Test
	void theSharedPrincipalLoginIsOnlySeededForASchoolWithNoAdmin() throws Exception {
		School withoutAdmin = schoolRepository.save(school("Admin-less School"));
		String registered = mockMvc.perform(post("/api/v1/schools").header(HttpHeaders.AUTHORIZATION, "")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Has Admin School", "address": "1 Road", "city": "Jaipur", "state": "Rajasthan",
								 "pincode": "302001", "contactEmail": "has-admin@school.example", "contactPhone": "9000000003",
								 "principalName": "Real Principal", "directorName": "Real Director",
								 "principalPhone": "9000000003", "adminPhone": "8000000003"}
								"""))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		UUID withAdmin = UUID.fromString(JsonPath.read(registered, "$.data.school.id"));

		principalSeeder.run(null);

		assertThat(credentialRepository.findBySchoolIdAndUsername(withoutAdmin.getId(), "9999999999"))
				.hasValueSatisfying(c -> assertThat(c.getRole()).isEqualTo(Role.ADMIN));
		assertThat(credentialRepository.findBySchoolIdAndUsername(withAdmin, "9999999999")).isEmpty();
	}

	/** Moves this phone's codes back in time, past the cooldown but inside the hour. */
	private void ageCodes(String phone) {
		jdbcTemplate.update("UPDATE otp_code SET created_at = DATEADD('SECOND', -60, created_at) WHERE phone = ?", phone);
	}

	private String teacherPhone() throws Exception {
		String phone = "6" + (UUID.randomUUID().toString() + UUID.randomUUID()).replaceAll("[^0-9]", "").substring(0, 9);
		mockMvc.perform(post("/api/v1/employees").header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "OTP Guard Teacher", "designation": "Teacher", "joinDate": "2024-04-01", "contactPhone": "%s"}
								""".formatted(phone)))
				.andExpect(status().isOk());
		return phone;
	}

	private ResultActions requestOtp(String phone) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/otp/request")
				.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "")
				.contentType(MediaType.APPLICATION_JSON).content("{\"phone\": \"" + phone + "\"}"));
	}

	private ResultActions verify(String phone, String otp) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/otp/verify")
				.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"phone\": \"" + phone + "\", \"otp\": \"" + otp + "\"}"));
	}

	private static School school(String name) {
		School school = new School();
		school.setName(name);
		school.setAddress("1 Road");
		school.setCity("Jaipur");
		school.setState("Rajasthan");
		school.setPincode("302001");
		school.setContactEmail("office@admin-less.example");
		school.setContactPhone("9000000004");
		school.setPrincipalName("Principal");
		school.setDirectorName("Director");
		return school;
	}

}
