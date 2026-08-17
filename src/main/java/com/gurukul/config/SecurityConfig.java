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
						.requestMatchers(HttpMethod.GET, "/api/v1/students/*/report-card/published-terms").hasAnyRole("TEACHER", "ADMIN", "STUDENT", "PARENT")
						// Section-wide report-card grid: admin, or that section's class teacher (checked in
						// the service layer) - same authority pattern as publish/fee-status above.
						.requestMatchers(HttpMethod.GET, "/api/v1/class-sections/*/report-cards").hasAnyRole("TEACHER", "ADMIN")
						// Term picker + backfill: same admin-or-class-teacher authority as publish, checked
						// in the service layer (AssessmentService.requireCanManageTerms).
						.requestMatchers(HttpMethod.GET, "/api/v1/class-sections/*/terms").hasAnyRole("TEACHER", "ADMIN")
						.requestMatchers(HttpMethod.PATCH, "/api/v1/class-sections/*/assessments/backfill-term").hasAnyRole("TEACHER", "ADMIN")
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
						// Academic Helper: any authenticated in-app role may ask. Which system prompt is
						// used (a student is taught the method, a teacher gets the answer key) is decided
						// in AiChatService from the caller's own role, never from the request body - so
						// this matcher only has to establish that there *is* a principal. The per-user
						// hourly cost cap is applied there too.
						.requestMatchers(HttpMethod.POST, "/api/v1/ai/chat")
						.hasAnyRole("ADMIN", "TEACHER", "STUDENT", "PARENT")
						// Announcements: creation role-gated here; the fine-grained "which section" check
						// happens in AnnouncementService via the caller's AuthPrincipal.
						.requestMatchers(HttpMethod.POST, "/api/v1/chat/announcements").hasAnyRole("ADMIN", "TEACHER")
						.requestMatchers(HttpMethod.GET, "/api/v1/chat/announcements").hasAnyRole("ADMIN", "TEACHER", "STUDENT")
						// The /ws STOMP handshake itself needs no matcher here - it stays under permitAll()
						// below; real auth happens on the STOMP CONNECT frame (see StompAuthChannelInterceptor).
						// Fee categories/structures: creation and per-structure assessment generation are
						// admin-only financial configuration; reads (needed for "My Class Fees" and fee
						// structure setup screens) are open to any staff member.
						.requestMatchers(HttpMethod.POST, "/api/v1/fee-categories").hasRole("ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/fee-categories").hasAnyRole("TEACHER", "ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/v1/fee-structures").hasRole("ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/v1/fee-structures/*/generate-assessments").hasRole("ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/fee-structures").hasAnyRole("TEACHER", "ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/fee-structures/*").hasAnyRole("TEACHER", "ADMIN")
						// Fee assessments/payments: staff (teacher/admin) manage these for their class/school;
						// a STUDENT may only ever act on their own assessment and a PARENT only a linked
						// child's, both already enforced in FeePaymentService (assertCanPayOrRecord /
						// listByStudent) - this role gate just adds the authentication this whole group was
						// previously missing entirely. GET .../fee-payments/{id} (a staff receipt lookup, per
						// PaymentReceiptScreen) has no such self-check, so it stays staff-only for now.
						.requestMatchers(HttpMethod.GET, "/api/v1/fee-assessments").hasAnyRole("TEACHER", "ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/students/*/fee-assessments").hasAnyRole("TEACHER", "ADMIN", "STUDENT", "PARENT")
						.requestMatchers(HttpMethod.POST, "/api/v1/fee-payments").hasAnyRole("TEACHER", "ADMIN", "STUDENT", "PARENT")
						.requestMatchers(HttpMethod.GET, "/api/v1/fee-payments/*").hasAnyRole("TEACHER", "ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/v1/fee-assessments/*/payment-request").hasAnyRole("TEACHER", "ADMIN", "STUDENT", "PARENT")
						.requestMatchers(HttpMethod.GET, "/api/v1/fee-assessments/*/payment-attempts/pending").hasAnyRole("TEACHER", "ADMIN", "STUDENT", "PARENT")
						.requestMatchers(HttpMethod.GET, "/api/v1/fee-assessments/*/payment-attempts").hasAnyRole("TEACHER", "ADMIN", "STUDENT", "PARENT")
						.requestMatchers(HttpMethod.POST, "/api/v1/payment-attempts/*/result").hasAnyRole("TEACHER", "ADMIN", "STUDENT", "PARENT")
						// Finance: aggregate ledger/fund-summary reporting, admin-only - not scoped to any
						// individual, so there's no self-service case to carve out here.
						.requestMatchers(HttpMethod.GET, "/api/v1/finance/transactions").hasRole("ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/finance/summary").hasRole("ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/v1/finance/transactions").hasRole("ADMIN")
						// Payroll: salary-structure/run administration is admin-only. Salary history and a
						// payslip are also read by the owning employee today via the "My Payslips" dashboard
						// tile (PrincipalDashboardScreen), but PayrollService doesn't check that the id in the
						// path is actually the caller's own record - same unenforced-self-service shape as
						// the employee attendance-history rule above. Matching that existing precedent here
						// (rather than a stricter admin-only rule) avoids breaking the current self-service
						// flow; closing the ownership gap itself is tracked as follow-up, not done here.
						.requestMatchers(HttpMethod.GET, "/api/v1/salary-structures").hasRole("ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/v1/salary-structures").hasRole("ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/v1/payroll/runs").hasRole("ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/v1/payroll/runs/*/process").hasRole("ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/v1/payroll/runs/*/pay").hasRole("ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/payroll/runs/*/lines").hasRole("ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/employees/*/salary-history").hasAnyRole("TEACHER", "ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/payroll/lines/*/payslip").hasAnyRole("TEACHER", "ADMIN")
						// Everything else is unchanged (no auth) for now - see auth ticket for phased retrofit scope.
						.anyRequest().permitAll());
		return http.build();
	}

}
