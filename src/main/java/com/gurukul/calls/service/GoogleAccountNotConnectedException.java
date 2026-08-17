package com.gurukul.calls.service;

/**
 * Thrown instead of falling back to Jitsi when {@code app.calls.require-google-meet} is set and
 * the call's host hasn't connected their own Google account. Kept as its own type (rather than a
 * generic IllegalStateException) so {@link com.gurukul.common.GlobalExceptionHandler} can attach a
 * stable {@code errorCode} the FE can branch on - "connect your account" needs a different UI
 * action than every other call-start failure, and matching on the message string would break the
 * moment the wording changes.
 */
public class GoogleAccountNotConnectedException extends RuntimeException {

	public static final String ERROR_CODE = "GOOGLE_ACCOUNT_NOT_CONNECTED";

	public GoogleAccountNotConnectedException() {
		super("Connect your Google account before starting a call");
	}

}
