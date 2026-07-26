package com.gurukul.auth.security;

import com.gurukul.config.SchoolContextFilter;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;

	@Override
	protected void doFilterInternal(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {

		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.startsWith("Bearer ")) {
			try {
				AuthPrincipal principal = jwtService.parseToken(header.substring(7));
				String schoolIdHeader = request.getHeader(SchoolContextFilter.SCHOOL_ID_HEADER);
				boolean schoolMatches = schoolIdHeader == null || schoolIdHeader.isBlank()
						|| principal.getSchoolId().toString().equalsIgnoreCase(schoolIdHeader.trim());
				if (schoolMatches) {
					List<SimpleGrantedAuthority> authorities =
							List.of(new SimpleGrantedAuthority("ROLE_" + principal.getRole().name()));
					SecurityContextHolder.getContext().setAuthentication(
							new UsernamePasswordAuthenticationToken(principal, null, authorities));
				}
			} catch (JwtException | IllegalArgumentException ex) {
				SecurityContextHolder.clearContext();
			}
		}

		filterChain.doFilter(request, response);
	}

}
