package com.gurukul.chat.bot.security;

import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.SchoolContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * Bot tools reuse existing, unmodified domain services (AttendanceService, FeePaymentService,
 * etc.) that internally call AuthContext.current()/SchoolContext.getSchoolId() - ThreadLocals
 * normally populated per-HTTP-request by SchoolContextFilter/JwtAuthenticationFilter. A STOMP
 * message-handling thread never runs those filters, so this seeds both ThreadLocals from the
 * bot's already-resolved AuthPrincipal for the duration of exactly one tool call, mirroring what
 * the two servlet filters do per HTTP request, and always clears them afterward.
 */
@Component
@RequiredArgsConstructor
public class PrincipalContextRunner {

	private final SchoolContext schoolContext;

	public <T> T runAs(AuthPrincipal principal, Supplier<T> action) {
		schoolContext.setSchoolId(principal.getSchoolId());
		Authentication previous = SecurityContextHolder.getContext().getAuthentication();
		List<SimpleGrantedAuthority> authorities =
				List.of(new SimpleGrantedAuthority("ROLE_" + principal.getRole().name()));
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(principal, null, authorities));
		try {
			return action.get();
		} finally {
			SecurityContextHolder.getContext().setAuthentication(previous);
			schoolContext.clear();
		}
	}

}
