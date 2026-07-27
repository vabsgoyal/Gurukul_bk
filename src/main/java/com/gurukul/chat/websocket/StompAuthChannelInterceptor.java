package com.gurukul.chat.websocket;

import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.auth.security.JwtService;
import com.gurukul.config.SchoolContextFilter;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Authenticates the STOMP CONNECT frame. The JWT arrives as a native STOMP header (there is no
 * HTTP request/response per frame after the initial handshake), gets validated via the existing
 * JwtService, and the resolved principal is stored on the session via accessor.setUser(...) so
 * every later frame from this session can retrieve it (SimpMessageHeaderAccessor.getUser()).
 *
 * <p>No SecurityConfig change is needed for the WS handshake itself - it falls through to the
 * existing anyRequest().permitAll(), because real auth happens here instead, one layer up.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

	private final JwtService jwtService;

	@Override
	public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
			return message;
		}

		String authHeader = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			throw new StompAuthenticationException("Missing or invalid Authorization header on CONNECT");
		}

		try {
			AuthPrincipal principal = jwtService.parseToken(authHeader.substring(7));
			String schoolIdHeader = accessor.getFirstNativeHeader(SchoolContextFilter.SCHOOL_ID_HEADER);
			if (schoolIdHeader != null && !schoolIdHeader.isBlank()
					&& !principal.getSchoolId().toString().equalsIgnoreCase(schoolIdHeader.trim())) {
				throw new StompAuthenticationException("X-School-Id does not match the token's school");
			}
			accessor.setUser(new StompPrincipal(principal));
		} catch (JwtException | IllegalArgumentException ex) {
			throw new StompAuthenticationException("Invalid or expired token", ex);
		}

		return message;
	}

}
