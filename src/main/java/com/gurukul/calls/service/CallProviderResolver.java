package com.gurukul.calls.service;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.calls.entity.CallProvider;
import com.gurukul.calls.googlemeet.GoogleMeetService;
import com.gurukul.calls.jitsi.JitsiBotService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Picks Google Meet or Jitsi for a new call/session and returns a ready-to-use room reference.
 * Google Meet is only attempted when explicitly preferred AND the specific host is an employee who
 * has connected their own Google account (see docs/google-meet-setup.md for why it must be
 * per-host, not one shared account) - any other case transparently falls back to the existing
 * Jitsi bot flow rather than hard-failing, since "provider configured system-wide" and "this one
 * host happens to have connected yet" are independent facts.
 */
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(CallProviderProperties.class)
public class CallProviderResolver {

	private final CallProviderProperties properties;
	private final GoogleMeetService googleMeetService;
	private final JitsiBotService jitsiBotService;

	public record Resolution(CallProvider provider, String roomNameOrUrl) {
	}

	/**
	 * For a call that needs to be ready to join RIGHT NOW (an immediate call, or starting a
	 * scheduled one): meetingTitle/startTime/endTime are only used for the Google Meet path (a real
	 * Calendar event needs them); the Jitsi path ignores them entirely. Throws
	 * IllegalStateException if room setup fails for the provider actually chosen - see
	 * JitsiBotService.warmRoom and GoogleMeetService.createMeeting for why each side refuses rather
	 * than silently proceeding.
	 */
	public Resolution resolveForImmediateUse(
			OwnerType hostOwnerType, UUID hostOwnerId, String meetingTitle, Instant startTime, Instant endTime) {
		if (shouldUseGoogleMeet(hostOwnerType, hostOwnerId)) {
			String joinUrl = googleMeetService.createMeeting(hostOwnerId, meetingTitle, startTime, endTime);
			return new Resolution(CallProvider.GOOGLE_MEET, joinUrl);
		}
		String roomName = RoomNames.generate();
		if (!jitsiBotService.warmRoom(roomName)) {
			throw new IllegalStateException("Could not start the call right now - please try again");
		}
		return new Resolution(CallProvider.JITSI, roomName);
	}

	/**
	 * For a call being SCHEDULED ahead of time, not started yet. Google Meet: the real Calendar
	 * event (with the real join link) is created immediately, same as scheduling a real calendar
	 * meeting - there's no equivalent of "warm the room later" for it. Jitsi: only a room name is
	 * generated here, deliberately NOT warmed - warming happens at actual start time (see
	 * ScheduledCallService.start), the same way it always has, since warming right when scheduled
	 * (possibly hours or days before anyone joins) is untested territory for the Jitsi bot and
	 * changes existing, working timing behavior for no benefit.
	 */
	public Resolution resolveForSchedule(
			OwnerType hostOwnerType, UUID hostOwnerId, String meetingTitle, Instant startTime, Instant endTime) {
		if (shouldUseGoogleMeet(hostOwnerType, hostOwnerId)) {
			String joinUrl = googleMeetService.createMeeting(hostOwnerId, meetingTitle, startTime, endTime);
			return new Resolution(CallProvider.GOOGLE_MEET, joinUrl);
		}
		return new Resolution(CallProvider.JITSI, RoomNames.generate());
	}

	private boolean shouldUseGoogleMeet(OwnerType hostOwnerType, UUID hostOwnerId) {
		return properties.preferredProvider() == CallProvider.GOOGLE_MEET
				&& hostOwnerType == OwnerType.EMPLOYEE
				&& googleMeetService.isConnected(hostOwnerId);
	}

}
