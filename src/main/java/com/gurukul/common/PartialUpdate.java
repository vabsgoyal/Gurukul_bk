package com.gurukul.common;

/**
 * Edit semantics for optional fields: a field left out of the request (null) keeps its current
 * value, and an empty string clears it. The app's edit forms don't send every optional field (a
 * student's RTE details, a teacher's email and type), so copying nulls straight through wiped
 * data on every save.
 */
public final class PartialUpdate {

	private PartialUpdate() {
	}

	public static String text(String current, String sent) {
		if (sent == null) {
			return current;
		}
		return sent.isBlank() ? null : sent;
	}

	/** Non-text fields can't be cleared this way - null only ever means "not sent". */
	public static <T> T value(T current, T sent) {
		return sent != null ? sent : current;
	}

}
