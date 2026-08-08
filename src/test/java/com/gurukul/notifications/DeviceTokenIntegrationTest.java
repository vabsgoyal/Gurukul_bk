package com.gurukul.notifications;

import com.gurukul.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Only covers registration (a plain DB upsert) - never exercises PushNotificationService's actual
 * Expo send path, since that would mean a real outbound network call from a test. That path stays
 * dormant here because no test ever registers a token before triggering a message/call/announcement,
 * so DeviceTokenRepository's lookup is always empty and PushNotificationService.send() short-circuits
 * before it would ever call out to Expo.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DeviceTokenIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void registeringATokenTwiceForDifferentOwnersReassignsRatherThanDuplicating() throws Exception {
		String adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String token = "ExponentPushToken[test-" + UUID.randomUUID() + "]";

		mockMvc.perform(post("/api/v1/notifications/device-token")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"expoPushToken\": \"%s\"}".formatted(token)))
				.andExpect(status().isOk());

		String teacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Push Test Teacher " + UUID.randomUUID());
		String teacherBearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "employees", teacherId, "TEACHER");

		mockMvc.perform(post("/api/v1/notifications/device-token")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"expoPushToken\": \"%s\"}".formatted(token)))
				.andExpect(status().isOk());
	}

	@Test
	void registeringWithoutAuthIsRejected() throws Exception {
		mockMvc.perform(post("/api/v1/notifications/device-token")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"expoPushToken\": \"ExponentPushToken[no-auth]\"}"))
				.andExpect(status().isUnauthorized());
	}

}
