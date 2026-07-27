package com.gurukul.chat.websocket;

/**
 * Thrown from a ChannelInterceptor to reject a STOMP frame (CONNECT or SUBSCRIBE) - Spring closes
 * the session / rejects the subscription in response to any RuntimeException thrown from preSend.
 */
public class StompAuthenticationException extends RuntimeException {

	public StompAuthenticationException(String message) {
		super(message);
	}

	public StompAuthenticationException(String message, Throwable cause) {
		super(message, cause);
	}

}
