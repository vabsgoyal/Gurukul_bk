package com.gurukul.chat.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnnouncementPushBodyTest {

	@Test
	void shortBodyIsSentAsIsWithWhitespaceTidied() {
		assertThat(AnnouncementService.pushBody("  School closed\n\ntomorrow  for Diwali. "))
				.isEqualTo("School closed tomorrow for Diwali.");
	}

	@Test
	void longBodyIsCutAtAWordWithAnEllipsis() {
		String body = "word ".repeat(60);

		String push = AnnouncementService.pushBody(body);

		assertThat(push).hasSizeLessThanOrEqualTo(AnnouncementService.PUSH_BODY_MAX).endsWith("word…");
	}

	@Test
	void emptyBodyFallsBackToNeutralWording() {
		assertThat(AnnouncementService.pushBody(null)).isEqualTo("Tap to open Smart Gurukul.");
		assertThat(AnnouncementService.pushBody("   ")).isEqualTo("Tap to open Smart Gurukul.");
	}

}
