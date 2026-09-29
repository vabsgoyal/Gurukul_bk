package com.gurukul.calls.service;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.calls.dto.CallEvent;
import com.gurukul.calls.entity.CallLog;
import com.gurukul.notifications.service.OwnerNameResolver;
import com.gurukul.notifications.service.PushChannel;
import com.gurukul.notifications.service.PushNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallEventPublisherTest {

	private static final UUID SCHOOL_ID = UUID.randomUUID();
	private static final UUID TEACHER_ID = UUID.randomUUID();
	private static final UUID STUDENT_ID = UUID.randomUUID();

	private PushNotificationService push;
	private OwnerNameResolver names;
	private CallEventPublisher publisher;

	@BeforeEach
	void setUp() {
		push = mock(PushNotificationService.class);
		names = mock(OwnerNameResolver.class);
		publisher = new CallEventPublisher(mock(SimpMessagingTemplate.class), push, names);
	}

	@Test
	void reminderIsPushedOnTheCallsChannel() {
		UUID callId = UUID.randomUUID();

		publisher.sendTo(SCHOOL_ID, OwnerType.STUDENT, STUDENT_ID,
				CallEvent.scheduledReminder(callId, "Maths doubt session", Instant.now().plus(Duration.ofMinutes(30))));

		verify(push).sendToOwner(eq(SCHOOL_ID), eq(OwnerType.STUDENT), eq(STUDENT_ID), eq(PushChannel.CALLS),
				eq("Maths doubt session"), eq("Starts in 30 minutes."), anyMap());
	}

	@Test
	void ringingCallHungUpByTheCallerIsNotPushedAsACancellation() {
		CallLog log = new CallLog();

		publisher.sendTo(SCHOOL_ID, OwnerType.STUDENT, STUDENT_ID, CallEvent.simple(CallEvent.Type.CALL_CANCELLED, log));

		verify(push, never()).sendToOwner(any(), any(), any(), any(), anyString(), anyString(), anyMap());
	}

	@Test
	void missedCallGoesToTheCalleeAndNamesTheCaller() {
		CallLog log = new CallLog();
		log.setSchoolId(SCHOOL_ID);
		log.setCallerOwnerType(OwnerType.EMPLOYEE);
		log.setCallerOwnerId(TEACHER_ID);
		log.setCalleeOwnerType(OwnerType.STUDENT);
		log.setCalleeOwnerId(STUDENT_ID);
		when(names.nameOf(SCHOOL_ID, OwnerType.EMPLOYEE, TEACHER_ID)).thenReturn(Optional.of("Priya Sharma"));

		publisher.pushMissedCall(log);

		verify(push).sendToOwner(eq(SCHOOL_ID), eq(OwnerType.STUDENT), eq(STUDENT_ID), eq(PushChannel.CALLS),
				eq("Missed video call"), eq("From Priya Sharma."), anyMap());
	}

	@Test
	void startsInCountsTheMinutesActuallyLeft() {
		assertThat(CallEventPublisher.startsIn(Instant.now().plus(Duration.ofMinutes(12)))).isEqualTo("Starts in 12 minutes.");
		assertThat(CallEventPublisher.startsIn(Instant.now().plusSeconds(20))).isEqualTo("Starts in a minute.");
		assertThat(CallEventPublisher.startsIn(null)).isEqualTo("Starts soon.");
	}

}
