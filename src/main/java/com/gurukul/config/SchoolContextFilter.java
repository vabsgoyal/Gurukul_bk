package com.gurukul.config;

import com.gurukul.common.SchoolContext;
import com.gurukul.schools.service.SchoolService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SchoolContextFilter extends OncePerRequestFilter {

	public static final String SCHOOL_ID_HEADER = "X-School-Id";

	private static final Pattern SCHOOL_BY_ID_PATH = Pattern.compile("/api/v1/schools/[0-9a-fA-F-]{36}");

	private final SchoolContext schoolContext;
	private final SchoolService schoolService;

	@Override
	protected void doFilterInternal(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {

		if (!request.getRequestURI().startsWith("/api/v1/")) {
			filterChain.doFilter(request, response);
			return;
		}

		if (isPublicEndpoint(request)) {
			filterChain.doFilter(request, response);
			return;
		}

		String header = request.getHeader(SCHOOL_ID_HEADER);
		if (header == null || header.isBlank()) {
			writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Missing X-School-Id header");
			return;
		}

		try {
			UUID schoolId = UUID.fromString(header.trim());
			schoolService.requireExists(schoolId);
			schoolContext.setSchoolId(schoolId);
			filterChain.doFilter(request, response);
		} catch (IllegalArgumentException ex) {
			writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid X-School-Id header: must be a UUID");
		} catch (com.gurukul.common.EntityNotFoundException ex) {
			writeError(response, HttpServletResponse.SC_BAD_REQUEST, "School not found");
		} finally {
			schoolContext.clear();
		}
	}

	private boolean isPublicEndpoint(HttpServletRequest request) {
		String uri = request.getRequestURI();
		if ("POST".equals(request.getMethod()) && "/api/v1/schools".equals(uri)) {
			return true;
		}
		return "GET".equals(request.getMethod()) && SCHOOL_BY_ID_PATH.matcher(uri).matches();
	}

	private void writeError(HttpServletResponse response, int status, String message) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"");
		response.getWriter().write("{\"success\":false,\"data\":null,\"message\":\"" + escaped + "\"}");
	}

}
