package com.gurukul.notifications.service;

/**
 * Android notification channel a push is posted to. The ids must match the channels the app
 * creates on launch (Gurukul_rn src/hooks/usePushNotifications.ts) - a push naming a channel the
 * device doesn't have falls back to Android's generic "Miscellaneous" channel. Channels are what a
 * user sees (and can mute one by one) under the app's notification settings.
 */
public enum PushChannel {
	MESSAGES("messages"),
	ANNOUNCEMENTS("announcements"),
	CALLS("calls"),
	ACADEMICS("academics"),
	/** Absence and fee-due alerts to parents. */
	ALERTS("alerts"),
	/** Quiz challenges and battle rooms opening. */
	GAMES("games");

	private final String androidChannelId;

	PushChannel(String androidChannelId) {
		this.androidChannelId = androidChannelId;
	}

	public String androidChannelId() {
		return androidChannelId;
	}
}
