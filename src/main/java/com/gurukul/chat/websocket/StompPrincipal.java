package com.gurukul.chat.websocket;

import com.gurukul.auth.security.AuthPrincipal;
import lombok.Getter;

import java.security.Principal;

/**
 * Wraps the AuthPrincipal resolved at STOMP CONNECT so it's retrievable via
 * SimpMessageHeaderAccessor.getUser() on every later frame from that session - see
 * StompAuthChannelInterceptor.
 */
@Getter
public class StompPrincipal implements Principal {

	private final AuthPrincipal authPrincipal;

	public StompPrincipal(AuthPrincipal authPrincipal) {
		this.authPrincipal = authPrincipal;
	}

	@Override
	public String getName() {
		return authPrincipal.getUsername();
	}

}
