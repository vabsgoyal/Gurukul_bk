package com.gurukul.auth;

import com.gurukul.auth.entity.Credential;
import com.gurukul.auth.entity.RefreshToken;
import com.gurukul.auth.repository.CredentialRepository;
import com.gurukul.auth.repository.RefreshTokenRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SessionRefreshIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CredentialRepository credentialRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	private String username;
	private final String password = "Password@123";

	@BeforeEach
	void provisionTeacher() throws Exception {
		String adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String employeeId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Refresh Teacher");
		username = "r-" + UUID.randomUUID().toString().substring(0, 12);
		mockMvc.perform(post("/api/v1/employees/" + employeeId + "/credentials")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username": "%s", "password": "%s", "role": "TEACHER"}
								""".formatted(username, password)))
				.andExpect(status().isOk());
	}

	@Test
	void loginReturnsARefreshTokenThatSlidesTheSessionForward() throws Exception {
		MvcResult login = login()
				.andExpect(jsonPath("$.data.refreshToken").exists())
				.andExpect(jsonPath("$.data.accessTokenExpiresAt").exists())
				.andExpect(jsonPath("$.data.refreshTokenExpiresAt").exists())
				.andReturn();
		Instant firstExpiry = Instant.parse(read(login, "$.data.refreshTokenExpiresAt"));
		assertThat(firstExpiry).isBetween(Instant.now().plus(Duration.ofDays(6)), Instant.now().plus(Duration.ofDays(8)));

		MvcResult refreshed = refresh(read(login, "$.data.refreshToken"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.username").value(username))
				.andReturn();
		assertThat(read(refreshed, "$.data.refreshToken")).isNotEqualTo(read(login, "$.data.refreshToken"));
		assertThat(Instant.parse(read(refreshed, "$.data.refreshTokenExpiresAt"))).isAfterOrEqualTo(firstExpiry);

		// The new access token works on an authenticated endpoint.
		mockMvc.perform(get("/api/v1/auth/profiles")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + read(refreshed, "$.data.token")))
				.andExpect(status().isOk());
	}

	@Test
	void aRefreshTokenWorksOnlyOnce() throws Exception {
		String first = read(login().andReturn(), "$.data.refreshToken");
		String second = read(refresh(first).andExpect(status().isOk()).andReturn(), "$.data.refreshToken");

		// Replayed straight away (e.g. two app requests racing): refused, but the newer session survives.
		refresh(first).andExpect(status().isUnauthorized());
		refresh(second).andExpect(status().isOk());
	}

	@Test
	void replayingAnOldRotatedTokenEndsEverySessionForThatLogin() throws Exception {
		String stolen = read(login().andReturn(), "$.data.refreshToken");
		String current = read(refresh(stolen).andReturn(), "$.data.refreshToken");
		String otherDevice = read(login().andReturn(), "$.data.refreshToken");

		// Pretend the stolen copy was rotated long enough ago to fall outside the race grace period.
		for (RefreshToken token : refreshTokenRepository.findAll()) {
			if (token.getRevokedAt() != null && token.getCredentialId().equals(credential().getId())) {
				token.setRevokedAt(Instant.now().minus(Duration.ofMinutes(5)));
				refreshTokenRepository.save(token);
			}
		}

		refresh(stolen).andExpect(status().isUnauthorized());
		refresh(current).andExpect(status().isUnauthorized());
		refresh(otherDevice).andExpect(status().isUnauthorized());
	}

	@Test
	void logoutEndsTheSessionAndAlwaysSucceeds() throws Exception {
		String refreshToken = read(login().andReturn(), "$.data.refreshToken");

		logout(refreshToken).andExpect(status().isOk());
		refresh(refreshToken).andExpect(status().isUnauthorized());
		logout(refreshToken).andExpect(status().isOk());
		logout("never-issued").andExpect(status().isOk());
	}

	@Test
	void aDisabledLoginCannotRefresh() throws Exception {
		String refreshToken = read(login().andReturn(), "$.data.refreshToken");
		Credential credential = credential();
		credential.setEnabled(false);
		credentialRepository.save(credential);

		refresh(refreshToken).andExpect(status().isUnauthorized());
	}

	@Test
	void garbageRefreshTokensAreRejected() throws Exception {
		refresh("not-a-real-token").andExpect(status().isUnauthorized());
	}

	private Credential credential() {
		return credentialRepository.findBySchoolIdAndUsername(UUID.fromString(SCHOOL_ID), username).orElseThrow();
	}

	private ResultActions login() throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username": "%s", "password": "%s"}
								""".formatted(username, password)))
				.andExpect(status().isOk());
	}

	/** No Authorization or X-School-Id: the access token is usually what just expired. */
	private ResultActions refresh(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\": \"" + refreshToken + "\"}"));
	}

	private ResultActions logout(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/logout")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\": \"" + refreshToken + "\"}"));
	}

	private static String read(MvcResult result, String path) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), path);
	}

}
