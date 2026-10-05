package com.gurukul.common;

import jakarta.servlet.http.HttpServletRequest;

/** The caller's address, for per-address rate limits on public endpoints. */
public final class ClientIp {

	private ClientIp() {
	}

	/**
	 * nginx fronts the app and appends the real peer address to X-Forwarded-For, so the LAST entry
	 * is the one our proxy saw. Earlier entries are client-supplied and trivially spoofable, which
	 * would let anyone rotate them to dodge a rate limit.
	 */
	public static String of(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			String[] parts = forwarded.split(",");
			String last = parts[parts.length - 1].trim();
			if (!last.isEmpty()) {
				return last;
			}
		}
		return request.getRemoteAddr();
	}

}
