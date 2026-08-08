package com.gurukul.auth;

import com.gurukul.auth.security.SupabaseJwtVerifier;
import com.gurukul.auth.security.SupabaseJwtVerifier.VerifiedSupabaseToken;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * app.auth.supabase.enabled=true here. SupabaseJwtVerifier is mocked so these tests never hit
 * the network/a real JWKS endpoint - only SupabaseAuthController/SupabaseAuthService's own
 * logic (linking, auto-create, phone-format fallback) is under test.
 */
@SpringBootTest(properties = "app.auth.supabase.enabled=true")
@AutoConfigureMockMvc
class SupabaseAuthIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SupabaseJwtVerifier supabaseJwtVerifier;

	@Test
	void unregisteredPhoneIsRejected() throws Exception {
		String token = "supabase-token-" + UUID.randomUUID();
		when(supabaseJwtVerifier.verify(eq(token)))
				.thenReturn(new VerifiedSupabaseToken(UUID.randomUUID(), "91" + uniqueLocalPhone()));

		mockMvc.perform(post("/api/v1/auth/otp/session")
						.header("X-School-Id", SCHOOL_ID)
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	@Test
	void invalidSupabaseTokenIsRejected() throws Exception {
		String token = "bad-token";
		when(supabaseJwtVerifier.verify(eq(token))).thenThrow(new BadCredentialsException("Invalid Supabase token"));

		mockMvc.perform(post("/api/v1/auth/otp/session")
						.header("X-School-Id", SCHOOL_ID)
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void teacherLogsInWithPhoneClaimMatchingContactPhoneVerbatim() throws Exception {
		String phone = uniqueLocalPhone();
		String employeeId = createEmployeeWithPhone(phone);
		String token = "supabase-token-" + UUID.randomUUID();
		when(supabaseJwtVerifier.verify(eq(token)))
				.thenReturn(new VerifiedSupabaseToken(UUID.randomUUID(), phone));

		mockMvc.perform(post("/api/v1/auth/otp/session")
						.header("X-School-Id", SCHOOL_ID)
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.role").value("TEACHER"))
				.andExpect(jsonPath("$.data.ownerId").value(employeeId))
				.andExpect(jsonPath("$.data.token").exists());
	}

	@Test
	void teacherLogsInWithPhoneClaimCarryingCountryCodePrefix() throws Exception {
		String phone = uniqueLocalPhone();
		String employeeId = createEmployeeWithPhone(phone);
		String token = "supabase-token-" + UUID.randomUUID();
		// Supabase's real claim shape: country code + local number, no "+".
		when(supabaseJwtVerifier.verify(eq(token)))
				.thenReturn(new VerifiedSupabaseToken(UUID.randomUUID(), "91" + phone));

		mockMvc.perform(post("/api/v1/auth/otp/session")
						.header("X-School-Id", SCHOOL_ID)
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.role").value("TEACHER"))
				.andExpect(jsonPath("$.data.ownerId").value(employeeId));
	}

	@Test
	void secondLoginBySameSupabaseUserReusesTheSameLinkedCredential() throws Exception {
		String phone = uniqueLocalPhone();
		createEmployeeWithPhone(phone);
		UUID supabaseUserId = UUID.randomUUID();
		String firstToken = "supabase-token-" + UUID.randomUUID();
		String secondToken = "supabase-token-" + UUID.randomUUID();
		when(supabaseJwtVerifier.verify(eq(firstToken))).thenReturn(new VerifiedSupabaseToken(supabaseUserId, phone));
		when(supabaseJwtVerifier.verify(eq(secondToken))).thenReturn(new VerifiedSupabaseToken(supabaseUserId, phone));

		MvcResult first = mockMvc.perform(post("/api/v1/auth/otp/session")
						.header("X-School-Id", SCHOOL_ID)
						.header("Authorization", "Bearer " + firstToken)
						.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andReturn();
		String firstOwnerId = JsonPath.read(first.getResponse().getContentAsString(), "$.data.ownerId");

		MvcResult second = mockMvc.perform(post("/api/v1/auth/otp/session")
						.header("X-School-Id", SCHOOL_ID)
						.header("Authorization", "Bearer " + secondToken)
						.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andReturn();
		String secondOwnerId = JsonPath.read(second.getResponse().getContentAsString(), "$.data.ownerId");

		assertThat(secondOwnerId).isEqualTo(firstOwnerId);
	}

	private String createEmployeeWithPhone(String phone) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/employees")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Supabase Teacher", "designation": "Teacher", "joinDate": "2024-04-01", "contactPhone": "%s"}
								""".formatted(phone)))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
	}

	private static String uniqueLocalPhone() {
		String digits = (UUID.randomUUID().toString() + UUID.randomUUID()).replaceAll("[^0-9]", "");
		return "9" + digits.substring(0, 9);
	}

}
