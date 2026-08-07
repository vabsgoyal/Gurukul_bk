package com.gurukul.auth.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class AuthContext {

	private AuthContext() {
	}

	public static AuthPrincipal current() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
			throw new AccessDeniedException("Authentication required");
		}
		return principal;
	}

	/** Like current(), but returns null instead of throwing when no principal is authenticated. */
	public static AuthPrincipal currentOrNull() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal ? principal : null;
	}

}
