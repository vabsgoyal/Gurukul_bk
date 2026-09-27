package com.gurukul.notifications.service;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.notifications.entity.DeviceToken;
import com.gurukul.notifications.repository.DeviceTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PushNotificationServiceTest {

	private static final UUID SCHOOL_ID = UUID.randomUUID();
	private static final UUID STUDENT_ID = UUID.randomUUID();

	private DeviceTokenRepository repository;
	private MockRestServiceServer expo;
	private PushNotificationService service;
	private final Map<String, DeviceToken> rows = new HashMap<>();

	@BeforeEach
	void setUp() {
		repository = mock(DeviceTokenRepository.class);
		RestClient.Builder builder = RestClient.builder();
		expo = MockRestServiceServer.bindTo(builder).build();
		service = new PushNotificationService(repository, builder.build());
	}

	private void registered(String... tokens) {
		for (String token : tokens) {
			DeviceToken row = deviceToken(token);
			rows.put(token, row);
			when(repository.findByExpoPushToken(token)).thenReturn(Optional.of(row));
		}
		when(repository.findAllBySchoolIdAndOwnerTypeAndOwnerIdIn(eq(SCHOOL_ID), eq(OwnerType.STUDENT), anyList()))
				.thenReturn(Arrays.stream(tokens).map(rows::get).toList());
	}

	private void send() {
		service.sendToOwner(SCHOOL_ID, OwnerType.STUDENT, STUDENT_ID, "Title", "Body", Map.of());
	}

	@Test
	void deletesTokensExpoReportsAsNoLongerRegistered() {
		registered("ExponentPushToken[alive]", "ExponentPushToken[uninstalled]");
		expo.expect(once(), requestTo(PushNotificationService.EXPO_PUSH_URL))
				.andRespond(withSuccess("""
						{"data": [
						  {"status": "ok", "id": "ticket-1"},
						  {"status": "error", "message": "not a registered push token",
						   "details": {"error": "DeviceNotRegistered"}}
						]}""", MediaType.APPLICATION_JSON));

		send();

		expo.verify();
		verify(repository).delete(rows.get("ExponentPushToken[uninstalled]"));
		verify(repository, never()).findByExpoPushToken("ExponentPushToken[alive]");
	}

	@Test
	void keepsTokensForOtherTicketErrors() {
		registered("ExponentPushToken[android]");
		expo.expect(once(), requestTo(PushNotificationService.EXPO_PUSH_URL))
				.andRespond(withSuccess("""
						{"data": [{"status": "error", "message": "Unable to retrieve the FCM server key",
						  "details": {"error": "InvalidCredentials"}}]}""", MediaType.APPLICATION_JSON));

		send();

		verify(repository, never()).delete(any());
	}

	@Test
	void splitsLargeSendsIntoExpoSizedBatches() {
		registered(IntStream.range(0, 150).mapToObj(i -> "ExponentPushToken[" + i + "]").toArray(String[]::new));
		expo.expect(times(2), requestTo(PushNotificationService.EXPO_PUSH_URL))
				.andRespond(withSuccess("{\"data\": []}", MediaType.APPLICATION_JSON));

		send();

		expo.verify();
	}

	@Test
	void neverThrowsWhenExpoIsDown() {
		registered("ExponentPushToken[alive]");
		expo.expect(once(), requestTo(PushNotificationService.EXPO_PUSH_URL)).andRespond(withServerError());

		send();

		verify(repository, never()).delete(any());
	}

	private static DeviceToken deviceToken(String expoPushToken) {
		DeviceToken token = new DeviceToken();
		token.setSchoolId(SCHOOL_ID);
		token.setOwnerType(OwnerType.STUDENT);
		token.setOwnerId(STUDENT_ID);
		token.setExpoPushToken(expoPushToken);
		return token;
	}

}
