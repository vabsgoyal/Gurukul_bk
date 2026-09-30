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
import java.util.List;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PushNotificationServiceTest {

	private static final UUID SCHOOL_ID = UUID.randomUUID();
	private static final UUID STUDENT_ID = UUID.randomUUID();

	private DeviceTokenRepository repository;
	private MockRestServiceServer expo;
	private PushNotificationService service;
	private NotificationInboxService inbox;
	private final Map<String, DeviceToken> rows = new HashMap<>();

	@BeforeEach
	void setUp() {
		repository = mock(DeviceTokenRepository.class);
		RestClient.Builder builder = RestClient.builder();
		expo = MockRestServiceServer.bindTo(builder).build();
		inbox = mock(NotificationInboxService.class);
		service = new PushNotificationService(repository, builder.build(), inbox);
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
		service.sendToOwner(SCHOOL_ID, OwnerType.STUDENT, STUDENT_ID, PushChannel.MESSAGES, "Title", "Body", Map.of());
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

	@Test
	void reachesParentsAndSendsTheAndroidChannelAtHighPriority() {
		UUID parentId = UUID.randomUUID();
		when(repository.findAllBySchoolIdAndOwnerTypeAndOwnerIdIn(SCHOOL_ID, OwnerType.PARENT, List.of(parentId)))
				.thenReturn(List.of(deviceToken("ExponentPushToken[parent]")));
		expo.expect(once(), requestTo(PushNotificationService.EXPO_PUSH_URL))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].to").value("ExponentPushToken[parent]"))
				.andExpect(jsonPath("$[0].channelId").value("academics"))
				.andExpect(jsonPath("$[0].priority").value("high"))
				.andRespond(withSuccess("{\"data\": [{\"status\": \"ok\"}]}", MediaType.APPLICATION_JSON));

		service.sendToOwner(SCHOOL_ID, OwnerType.PARENT, parentId, PushChannel.ACADEMICS, "Title", "Body", Map.of());

		expo.verify();
	}

	@Test
	void sendsDifferentlyWordedNotificationsInOneBatchAndListsEachPersonOnce() {
		UUID aaravsParent = UUID.randomUUID();
		UUID studentId = UUID.randomUUID();
		when(repository.findAllBySchoolIdAndOwnerTypeAndOwnerIdIn(SCHOOL_ID, OwnerType.PARENT, List.of(aaravsParent)))
				.thenReturn(List.of(deviceToken("ExponentPushToken[parent]")));
		when(repository.findAllBySchoolIdAndOwnerTypeAndOwnerIdIn(SCHOOL_ID, OwnerType.STUDENT, List.of(studentId)))
				.thenReturn(List.of(deviceToken("ExponentPushToken[student]")));
		expo.expect(once(), requestTo(PushNotificationService.EXPO_PUSH_URL))
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].title").value("Your report card is ready"))
				.andExpect(jsonPath("$[1].title").value("Aarav's report card is ready"))
				.andRespond(withSuccess("{\"data\": []}", MediaType.APPLICATION_JSON));

		service.sendEach(SCHOOL_ID, PushChannel.ACADEMICS, List.of(
				new PushNotificationService.Notification(List.of(new PushNotificationService.Recipient(OwnerType.STUDENT, studentId)),
						"Your report card is ready", "Body", Map.of()),
				// The same parent listed twice (two children in one scope) still gets one message.
				new PushNotificationService.Notification(List.of(
						new PushNotificationService.Recipient(OwnerType.PARENT, aaravsParent),
						new PushNotificationService.Recipient(OwnerType.PARENT, aaravsParent)),
						"Aarav's report card is ready", "Body", Map.of())));

		expo.verify();
	}

	@Test
	void sendsNothingWhenNobodyHasADevice() {
		when(repository.findAllBySchoolIdAndOwnerTypeAndOwnerIdIn(eq(SCHOOL_ID), any(), anyList())).thenReturn(List.of());

		service.sendToOwner(SCHOOL_ID, OwnerType.EMPLOYEE, UUID.randomUUID(), PushChannel.CALLS, "Title", "Body", Map.of());

		expo.verify();
	}

	@Test
	void savesEveryNotificationToTheInboxEvenWithoutADevice() {
		UUID parentId = UUID.randomUUID();
		when(repository.findAllBySchoolIdAndOwnerTypeAndOwnerIdIn(eq(SCHOOL_ID), any(), anyList())).thenReturn(List.of());
		Map<String, Object> data = Map.of("type", "ANNOUNCEMENT");

		service.sendToOwner(SCHOOL_ID, OwnerType.PARENT, parentId, PushChannel.ANNOUNCEMENTS, "Title", "Body", data);

		verify(inbox).record(SCHOOL_ID, List.of(new PushNotificationService.Recipient(OwnerType.PARENT, parentId)),
				"Title", "Body", data);
	}

	@Test
	void stillPushesWhenTheInboxWriteFails() {
		registered("ExponentPushToken[alive]");
		org.mockito.Mockito.doThrow(new RuntimeException("db down")).when(inbox).record(any(), anyList(), any(), any(), any());
		expo.expect(once(), requestTo(PushNotificationService.EXPO_PUSH_URL))
				.andRespond(withSuccess("{\"data\": []}", MediaType.APPLICATION_JSON));

		send();

		expo.verify();
	}

	@Test
	void sendOncePushesOnlyTheRecipientsNewlyClaimed() {
		UUID alreadyAlerted = UUID.randomUUID();
		UUID fresh = UUID.randomUUID();
		PushNotificationService.Recipient freshRecipient = new PushNotificationService.Recipient(OwnerType.PARENT, fresh);
		when(inbox.unclaimed(anyList(), eq("ABSENCE:x"))).thenReturn(List.of(freshRecipient));
		when(repository.findAllBySchoolIdAndOwnerTypeAndOwnerIdIn(SCHOOL_ID, OwnerType.PARENT, List.of(fresh)))
				.thenReturn(List.of(deviceToken("ExponentPushToken[fresh]")));
		expo.expect(once(), requestTo(PushNotificationService.EXPO_PUSH_URL))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].to").value("ExponentPushToken[fresh]"))
				.andExpect(jsonPath("$[0].channelId").value("alerts"))
				.andRespond(withSuccess("{\"data\": []}", MediaType.APPLICATION_JSON));

		int sent = service.sendOnce(SCHOOL_ID, PushChannel.ALERTS, new PushNotificationService.Notification(
				List.of(new PushNotificationService.Recipient(OwnerType.PARENT, alreadyAlerted), freshRecipient),
				"Absent", "Body", Map.of("type", "ABSENCE_ALERT")), "ABSENCE:x");

		expo.verify();
		org.junit.jupiter.api.Assertions.assertEquals(1, sent);
		// The inbox row is written after the push, for just the recipient who was pushed.
		verify(inbox).claim(eq(SCHOOL_ID), eq(List.of(freshRecipient)), any(), any(), any(), eq("ABSENCE:x"));
		verify(inbox, never()).record(any(), anyList(), any(), any(), any());
	}

	@Test
	void sendOnceSendsNothingWhenItCannotTellWhoAlreadyHasTheAlert() {
		when(inbox.unclaimed(anyList(), any())).thenThrow(new RuntimeException("database unavailable"));

		int sent = service.sendOnce(SCHOOL_ID, PushChannel.ALERTS, new PushNotificationService.Notification(
				List.of(new PushNotificationService.Recipient(OwnerType.PARENT, UUID.randomUUID())),
				"Absent", "Body", Map.of()), "ABSENCE:y");

		org.junit.jupiter.api.Assertions.assertEquals(0, sent);
		verify(inbox, never()).claim(any(), anyList(), any(), any(), any(), any());
	}

	@Test
	void aFailedInboxWriteAfterThePushIsLoggedNotThrown() {
		UUID parent = UUID.randomUUID();
		PushNotificationService.Recipient recipient = new PushNotificationService.Recipient(OwnerType.PARENT, parent);
		when(inbox.unclaimed(anyList(), eq("ABSENCE:z"))).thenReturn(List.of(recipient));
		when(inbox.claim(any(), anyList(), any(), any(), any(), any())).thenThrow(new RuntimeException("duplicate key"));
		when(repository.findAllBySchoolIdAndOwnerTypeAndOwnerIdIn(SCHOOL_ID, OwnerType.PARENT, List.of(parent)))
				.thenReturn(List.of(deviceToken("ExponentPushToken[z]")));
		expo.expect(once(), requestTo(PushNotificationService.EXPO_PUSH_URL))
				.andRespond(withSuccess("{\"data\": []}", MediaType.APPLICATION_JSON));

		int sent = service.sendOnce(SCHOOL_ID, PushChannel.ALERTS,
				new PushNotificationService.Notification(List.of(recipient), "Absent", "Body", Map.of()), "ABSENCE:z");

		expo.verify();
		org.junit.jupiter.api.Assertions.assertEquals(1, sent);
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
