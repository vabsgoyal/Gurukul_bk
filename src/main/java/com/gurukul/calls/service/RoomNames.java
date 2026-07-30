package com.gurukul.calls.service;

import java.security.SecureRandom;

/**
 * Public meet.jit.si rooms have no access control by name - anyone who knows/guesses a room name
 * can join it. The only mitigation available without self-hosting Jitsi is making room names
 * long and unguessable, and never exposing them outside authenticated, permission-checked app
 * screens. 128 bits of randomness (32 hex chars) is the mitigation here.
 */
final class RoomNames {

	private static final SecureRandom RANDOM = new SecureRandom();

	private RoomNames() {
	}

	static String generate() {
		byte[] bytes = new byte[16];
		RANDOM.nextBytes(bytes);
		StringBuilder hex = new StringBuilder("gurukul-");
		for (byte b : bytes) {
			hex.append(String.format("%02x", b));
		}
		return hex.toString();
	}

}
