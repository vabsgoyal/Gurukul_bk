package com.gurukul.config;

import com.gurukul.auth.security.JwtAuthenticationFilter;
import com.gurukul.auth.security.JwtService;
import com.gurukul.auth.security.RestAccessDeniedHandler;
import com.gurukul.auth.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtService jwtService;

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(new RestAuthenticationEntryPoint())
						.accessDeniedHandler(new RestAccessDeniedHandler()))
				.addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
						.permitAll()
						// Attendance: teacher/admin mark & view a section's roster; students, teachers and
						// admins may view a student's own history (self-check enforced in the service layer).
						.requestMatchers(HttpMethod.POST, "/api/v1/class-sections/*/attendance").hasAnyRole("TEACHER", "ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/class-sections/*/attendance").hasAnyRole("TEACHER", "ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/class-sections/*/attendance/history").hasAnyRole("TEACHER", "ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/students/*/attendance").hasAnyRole("TEACHER", "ADMIN", "STUDENT")
						// Staff attendance: admin-only.
						.requestMatchers(HttpMethod.POST, "/api/v1/staff-attendance").hasRole("ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/staff-attendance").hasRole("ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/employees/*/attendance").hasAnyRole("TEACHER", "ADMIN")
						// Class teacher assignment: admin-only.
						.requestMatchers(HttpMethod.PATCH, "/api/v1/class-sections/*/class-teacher").hasRole("ADMIN")
						// Assessments: teachers/admins author them; students may only ever read (GETs stay
						// on the general permitAll() below, matching every other read-only listing today).
						.requestMatchers(HttpMethod.POST, "/api/v1/class-sections/*/assessments").hasAnyRole("TEACHER", "ADMIN")
						.requestMatchers(HttpMethod.PUT, "/api/v1/assessments/*").hasAnyRole("TEACHER", "ADMIN")
						.requestMatchers(HttpMethod.DELETE, "/api/v1/assessments/*").hasAnyRole("TEACHER", "ADMIN")
						// Credential provisioning: admin-only.
						.requestMatchers(HttpMethod.POST, "/api/v1/employees/*/credentials", "/api/v1/students/*/credentials")
						.hasRole("ADMIN")
						// Chat: conversations/messages/bot need to know the sender's identity, so all require
						// auth. Student-vs-student pairing is rejected in the service layer (depends on
						// resolving both parties' owner types, which method+path matching can't express).
						.requestMatchers(HttpMethod.POST, "/api/v1/chat/conversations").hasAnyRole("ADMIN", "TEACHER", "STUDENT")
						.requestMatchers(HttpMethod.GET, "/api/v1/chat/conversations").hasAnyRole("ADMIN", "TEACHER", "STUDENT")
						.requestMatchers(HttpMethod.GET, "/api/v1/chat/conversations/*/messages").hasAnyRole("ADMIN", "TEACHER", "STUDENT")
						.requestMatchers(HttpMethod.POST, "/api/v1/chat/bot/conversation").hasAnyRole("ADMIN", "TEACHER", "STUDENT")
						// Announcements: creation role-gated here; the fine-grained "which section" check
						// happens in AnnouncementService via the caller's AuthPrincipal.
						.requestMatchers(HttpMethod.POST, "/api/v1/chat/announcements").hasAnyRole("ADMIN", "TEACHER")
						.requestMatchers(HttpMethod.GET, "/api/v1/chat/announcements").hasAnyRole("ADMIN", "TEACHER", "STUDENT")
						// The /ws STOMP handshake itself needs no matcher here - it stays under permitAll()
						// below; real auth happens on the STOMP CONNECT frame (see StompAuthChannelInterceptor).
						// Everything else is unchanged (no auth) for now - see auth ticket for phased retrofit scope.
						.anyRequest().permitAll());
		return http.build();
	}

}
