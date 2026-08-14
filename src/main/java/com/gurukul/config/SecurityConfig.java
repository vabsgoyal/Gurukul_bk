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
						.requestMatchers(HttpMethod.GET, "/api/v1/students/*/attendance").hasAnyRole("TEACHER", "ADMIN", "STUDENT", "PARENT")
						// Staff attendance: bulk admin-entry is admin-only; self-mark (geofenced check-in) is
						// for the employee themselves, teacher or admin.
						.requestMatchers(HttpMethod.POST, "/api/v1/staff-attendance/self-mark").hasAnyRole("TEACHER", "ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/v1/staff-attendance").hasRole("ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/staff-attendance").hasRole("ADMIN")
						// School location (geofence center/radius for self-mark attendance): admin-only.
						.requestMatchers(HttpMethod.PUT, "/api/v1/schools/*/location").hasRole("ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/employees/*/attendance").hasAnyRole("TEACHER", "ADMIN")
						// Class teacher assignment: admin-only.
						.requestMatchers(HttpMethod.PATCH, "/api/v1/class-sections/*/class-teacher").hasRole("ADMIN")
						// Push notification device registration: any authenticated session registers its
						// own device, regardless of role.
						.requestMatchers(HttpMethod.POST, "/api/v1/notifications/device-token").authenticated()
						// Assessments: teachers/admins author them; students may only ever read (GETs stay
						// on the general permitAll() below, matching every other read-only listing today).
						.requestMatchers(HttpMethod.POST, "/api/v1/class-sections/*/assessments").hasAnyRole("TEACHER", "ADMIN")
						.requestMatchers(HttpMethod.PUT, "/api/v1/assessments/*").hasAnyRole("TEACHER", "ADMIN")
						.requestMatchers(HttpMethod.DELETE, "/api/v1/assessments/*").hasAnyRole("TEACHER", "ADMIN")
						// Exam results: entry/roster-view is a teacher/admin tool - a student sees their own
						// marks through the report-card/grade-card endpoint below, not this roster shape.
						.requestMatchers(HttpMethod.POST, "/api/v1/assessments/*/results").hasAnyRole("TEACHER", "ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/assessments/*/results").hasAnyRole("TEACHER", "ADMIN")
						// Grading scale: any authenticated role reads it (needed to interpret a grade card),
						// only an admin may redefine the bands.
						.requestMatchers(HttpMethod.PUT, "/api/v1/grading-scale").hasRole("ADMIN")
						// Report cards: publishing is admin or that section's class teacher (checked in the
						// service layer); viewing is student/teacher/admin/parent with the
						// student-sees-only-their-own-and-only-once-published check in the service layer.
						.requestMatchers(HttpMethod.POST, "/api/v1/class-sections/*/report-cards/publish").hasAnyRole("TEACHER", "ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/students/*/report-card").hasAnyRole("TEACHER", "ADMIN", "STUDENT", "PARENT")
						// Class-section fee status: admin, or that section's own class teacher (checked in
						// the service layer) - a class-fees overview tile for a class teacher.
						.requestMatchers(HttpMethod.GET, "/api/v1/class-sections/*/fee-status").hasAnyRole("TEACHER", "ADMIN")
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
						// Academic Helper: server-side proxy for the Bedrock/Anthropic tutor chat - requires
						// auth so the client-embedded-AWS-key issue it replaces (SECURITY_AND_ACCESS.md SS9.3)
						// isn't swapped for an unauthenticated backend endpoint instead.
						.requestMatchers(HttpMethod.POST, "/api/v1/academic-helper/ask").hasAnyRole("ADMIN", "TEACHER", "STUDENT")
						// The /ws STOMP handshake itself needs no matcher here - it stays under permitAll()
						// below; real auth happens on the STOMP CONNECT frame (see StompAuthChannelInterceptor).
						// Everything else is unchanged (no auth) for now - see auth ticket for phased retrofit scope.
						.anyRequest().permitAll());
		return http.build();
	}

}
