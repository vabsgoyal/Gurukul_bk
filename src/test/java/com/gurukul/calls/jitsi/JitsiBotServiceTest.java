package com.gurukul.calls.jitsi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No live browser here (see execution-plan/summary notes on why: CI shouldn't depend on hitting
 * the real public meet.jit.si). {@link JitsiBotService#stillWaitingForModerator} is tested against
 * the exact text captured by manually driving a headless browser against a fresh anonymous
 * meet.jit.si room during this feature's design - see docs/jitsi-bot-setup.md.
 */
class JitsiBotServiceTest {

	@Test
	void detectsTheRealWaitingForModeratorMessage() {
		String observed = "Asking to join meeting…\nSome Room Name\n"
				+ "The conference has not yet started because no moderators have yet arrived. "
				+ "If you'd like to become a moderator please log-in. Otherwise, please wait.\n"
				+ "Log-in\nYour devices are working properly";

		assertThat(JitsiBotService.stillWaitingForModerator(observed)).isTrue();
	}

	@Test
	void doesNotFlagAnOrdinaryConferenceScreenAsWaiting() {
		assertThat(JitsiBotService.stillWaitingForModerator("Some Room Name\nMute\nCamera\nLeave")).isFalse();
	}

	@Test
	void treatsBlankOrNullTextAsNotWaiting() {
		assertThat(JitsiBotService.stillWaitingForModerator(null)).isFalse();
		assertThat(JitsiBotService.stillWaitingForModerator("")).isFalse();
	}

	@Test
	void warmRoomIsANoOpWhenDisabled() {
		JitsiBotProperties disabled = new JitsiBotProperties(false, "https://meet.jit.si", "", "", "", 20);
		JitsiBotService service = new JitsiBotService(disabled);

		// Must return immediately without touching a browser - no exception, no hang - and report
		// success so callers don't refuse to start calls just because the bot feature is off.
		assertThat(service.warmRoom("some-room-name")).isTrue();
	}

	@Test
	void warmRoomIsANoOpWhenEnabledButNoProfileDirConfigured() {
		JitsiBotProperties noProfile = new JitsiBotProperties(true, "https://meet.jit.si", "  ", "", "", 20);
		JitsiBotService service = new JitsiBotService(noProfile);

		assertThat(service.warmRoom("some-room-name")).isTrue();
	}

	@Test
	void roomUrlDisablesThePrejoinScreenAndCarriesTheRoomName() {
		JitsiBotProperties properties = new JitsiBotProperties(true, "https://meet.jit.si", "/tmp/profile", "", "", 20);
		JitsiBotService service = new JitsiBotService(properties);

		String url = service.roomUrl("my-room-123");

		assertThat(url).startsWith("https://meet.jit.si/my-room-123#");
		assertThat(url).contains("config.prejoinConfig.enabled=false");
	}

}
