package com.gurukul.config;

import com.gurukul.auth.security.JwtAuthenticationFilter;
import com.gurukul.auth.security.JwtService;
import com.gurukul.auth.security.RestAccessDeniedHandler;
import com.gurukul.auth.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtService jwtService;

	@Value("${app.leads.allowed-origins}")
	private String leadsAllowedOrigins;

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				// CORS is registered for /api/v1/leads ONLY (the marketing site's demo form). Every other
				// path has no CORS config, so browsers keep blocking cross-origin calls to it as before.
				.cors(cors -> cors.configurationSource(leadsCorsConfigurationSource()))
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
						// School profile, including the bank account/IFSC/UPI VPA that fee payments are routed
						// to: admin-only. Was previously unauthenticated. SchoolController additionally checks
						// the admin belongs to the school being edited.
						.requestMatchers(HttpMethod.PUT, "/api/v1/schools/*").hasRole("ADMIN")
						// School logo (shown on report-card PDFs): admin-only; SchoolController additionally checks
						// the admin belongs to the school in the path.
						.requestMatchers(HttpMethod.POST, "/api/v1/schools/*/logo/presign").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PUT, "/api/v1/schools/*/logo").hasRole("ADMIN")
						.requestMatchers(HttpMethod.DELETE, "/api/v1/schools/*/logo").hasRole("ADMIN")
						// Attendance devices (RFID/fingerprint/face) and identifier enrollment: admin-only to
						// manage; reading an enrollment list is also open to a teacher. The device-event
						// ingestion endpoint (/api/v1/attendance/device-events) is deliberately NOT listed
						// here - it authenticates via X-Device-Key, not a human JWT, checked manually in
						// AttendanceDeviceEventService (same pattern as /api/v1/ops/admin-backfill).
						.requestMatchers("/api/v1/attendance-devices/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/v1/students/*/attendance-identifiers", "/api/v1/employees/*/attendance-identifiers")
						.hasRole("ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/students/*/attendance-identifiers", "/api/v1/employees/*/attendance-identifiers")
						.hasAnyRole("TEACHER", "ADMIN")
						.requestMatchers(HttpMethod.DELETE, "/api/v1/attendance-identifiers/*").hasRole("ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/employees/*/attendance").hasAnyRole("TEACHER", "ADMIN")
						// Class teacher assignment: admin-only.
						.requestMatchers(HttpMethod.PATCH, "/api/v1/class-sections/*/class-teacher").hasRole("ADMIN")
						// Push notification device registration: any authenticated session registers its
						// own device, regardless of role.
						.requestMatchers(HttpMethod.POST, "/api/v1/notifications/device-token").authenticated()
						// Notification inbox: any signed-in role reads/marks its own rows only - the owner
						// always comes from the token (NotificationInboxService), never the request.
						.requestMatchers(HttpMethod.GET, "/api/v1/notifications", "/api/v1/notifications/unread-count").authenticated()
						.requestMatchers(HttpMethod.POST, "/api/v1/notifications/*/read", "/api/v1/notifications/read-all").authenticated()
						// Profile picker: list/switch between the profiles that share the caller's phone.
						.requestMatchers("/api/v1/auth/profiles", "/api/v1/auth/profiles/**").authenticated()
						// Assessments: teachers/admins author them; any logged-in role may read (GETs fall to
						// the authenticated() default below).
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
						// Report-card PDF: same roles as the JSON view above, and the same service-layer checks
						// (it calls ReportCardService.getReportCard). A distinct path, so it needs its own matcher -
						// an unmatched path would fall through to the looser authenticated() default below.
						.requestMatchers(HttpMethod.GET, "/api/v1/students/*/report-card.pdf").hasAnyRole("TEACHER", "ADMIN", "STUDENT", "PARENT")
						// Section-wide report-card grid: admin, or that section's class teacher (checked in
						// the service layer) - same authority pattern as publish/fee-status above.
						.requestMatchers(HttpMethod.GET, "/api/v1/class-sections/*/report-cards").hasAnyRole("TEACHER", "ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/class-sections/*/report-cards.pdf").hasAnyRole("TEACHER", "ADMIN")
						// Term picker + backfill: same admin-or-class-teacher authority as publish, checked
						// in the service layer (AssessmentService.requireCanManageTerms).
						.requestMatchers(HttpMethod.GET, "/api/v1/class-sections/*/terms").hasAnyRole("TEACHER", "ADMIN")
						.requestMatchers(HttpMethod.PATCH, "/api/v1/class-sections/*/assessments/backfill-term").hasAnyRole("TEACHER", "ADMIN")
						// Class-section fee status: admin, or that section's own class teacher (checked in
						// the service layer) - a class-fees overview tile for a class teacher.
						.requestMatchers(HttpMethod.GET, "/api/v1/class-sections/*/fee-status").hasAnyRole("TEACHER", "ADMIN")
						// Timetable: only an admin edits the bell schedule or a section's timetable. Any
						// logged-in role reads; which section a teacher/student/parent may read is checked
						// in TimetableService.
						.requestMatchers(HttpMethod.PUT, "/api/v1/periods").hasRole("ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/periods").hasAnyRole("TEACHER", "ADMIN", "STUDENT", "PARENT")
						.requestMatchers(HttpMethod.PUT, "/api/v1/class-sections/*/timetable").hasRole("ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/class-sections/*/timetable").hasAnyRole("TEACHER", "ADMIN", "STUDENT", "PARENT")
						.requestMatchers(HttpMethod.GET, "/api/v1/timetable/me").hasAnyRole("TEACHER", "ADMIN", "STUDENT", "PARENT")
						// ID cards: the fine-grained "whose card" rules (self / linked parent / admin view, and
						// self-or-linked-parent-only edits) live in IdCardService; these matchers add the
						// authentication and coarse role gate. Sheets are admin-only, verify is staff-only.
						.requestMatchers(HttpMethod.GET, "/api/v1/id-cards/class-sections/*/sheet.pdf", "/api/v1/id-cards/staff/sheet.pdf")
						.hasRole("ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/id-cards/verify").hasAnyRole("TEACHER", "ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/id-cards/me", "/api/v1/id-cards/students/*", "/api/v1/id-cards/students/*/card.pdf")
						.hasAnyRole("TEACHER", "ADMIN", "STUDENT", "PARENT")
						.requestMatchers(HttpMethod.PUT, "/api/v1/id-cards/students/*/profile", "/api/v1/id-cards/students/*/photo")
						.hasAnyRole("STUDENT", "PARENT")
						.requestMatchers(HttpMethod.POST, "/api/v1/id-cards/students/*/photo/presign").hasAnyRole("STUDENT", "PARENT")
						.requestMatchers(HttpMethod.DELETE, "/api/v1/id-cards/students/*/photo").hasAnyRole("STUDENT", "PARENT")
						.requestMatchers(HttpMethod.GET, "/api/v1/id-cards/employees/*", "/api/v1/id-cards/employees/*/card.pdf")
						.hasAnyRole("TEACHER", "ADMIN")
						.requestMatchers(HttpMethod.PUT, "/api/v1/id-cards/employees/*/profile", "/api/v1/id-cards/employees/*/photo")
						.hasAnyRole("TEACHER", "ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/v1/id-cards/employees/*/photo/presign").hasAnyRole("TEACHER", "ADMIN")
						.requestMatchers(HttpMethod.DELETE, "/api/v1/id-cards/employees/*/photo").hasAnyRole("TEACHER", "ADMIN")
						// Anything else under /id-cards (e.g. a typo'd path) is never left open.
						.requestMatchers("/api/v1/id-cards/**").denyAll()
						// Credential provisioning: admin-only.
						.requestMatchers(HttpMethod.POST, "/api/v1/employees/*/credentials", "/api/v1/students/*/credentials")
						.hasRole("ADMIN")
						// Chat: conversations/messages/bot need to know the sender's identity, so all require
						// auth. Who may pair with whom (no student-to-student; a parent only with their
						// child's teachers and the school's admins, a teacher only with their students'
						// parents) is decided in the service layer (ConversationService/ChatContactService),
						// since it depends on resolving both parties, which method+path matching can't express.
						// Reading/sending is gated on being a participant (requireParticipant), the same check
						// the STOMP subscribe/send path uses.
						.requestMatchers(HttpMethod.POST, "/api/v1/chat/conversations").hasAnyRole("ADMIN", "TEACHER", "STUDENT", "PARENT")
						.requestMatchers(HttpMethod.GET, "/api/v1/chat/conversations").hasAnyRole("ADMIN", "TEACHER", "STUDENT", "PARENT")
						.requestMatchers(HttpMethod.GET, "/api/v1/chat/conversations/*/messages").hasAnyRole("ADMIN", "TEACHER", "STUDENT", "PARENT")
						.requestMatchers(HttpMethod.POST, "/api/v1/chat/conversations/*/read").hasAnyRole("ADMIN", "TEACHER", "STUDENT", "PARENT")
						.requestMatchers(HttpMethod.GET, "/api/v1/chat/unread-count").hasAnyRole("ADMIN", "TEACHER", "STUDENT", "PARENT")
						.requestMatchers(HttpMethod.POST, "/api/v1/chat/conversations/*/attachments/presign")
						.hasAnyRole("ADMIN", "TEACHER", "STUDENT", "PARENT")
						.requestMatchers(HttpMethod.GET, "/api/v1/chat/contacts").hasAnyRole("ADMIN", "TEACHER", "PARENT")
						.requestMatchers(HttpMethod.POST, "/api/v1/chat/bot/conversation").hasAnyRole("ADMIN", "TEACHER", "STUDENT")
						// Academic Helper: any authenticated in-app role may ask. Which system prompt is
						// used (a student is taught the method, a teacher gets the answer key) is decided
						// in AiChatService from the caller's own role, never from the request body - so
						// this matcher only has to establish that there *is* a principal. The per-user
						// hourly cost cap is applied there too.
						.requestMatchers(HttpMethod.POST, "/api/v1/ai/chat")
						.hasAnyRole("ADMIN", "TEACHER", "STUDENT", "PARENT")
						// AI quiz generator: staff only. That a TEACHER may only generate for themselves and
						// for a section + subject they teach is checked in QuizGeneratorService.
						.requestMatchers(HttpMethod.POST, "/api/v1/teachers/*/ai/quiz-generator").hasAnyRole("ADMIN", "TEACHER")
						// Bulk question-bank save (reviewed AI quiz questions): staff only; the "subject +
						// grade you teach" check for a TEACHER is in ArenaService.bulkCreateQuestions.
						.requestMatchers(HttpMethod.POST, "/api/v1/gamification/arena/questions/bulk").hasAnyRole("ADMIN", "TEACHER")
						// Announcements: creation role-gated here; the fine-grained "which section" check
						// happens in AnnouncementService via the caller's AuthPrincipal.
						.requestMatchers(HttpMethod.POST, "/api/v1/chat/announcements").hasAnyRole("ADMIN", "TEACHER")
						.requestMatchers(HttpMethod.GET, "/api/v1/chat/announcements").hasAnyRole("ADMIN", "TEACHER", "STUDENT", "PARENT")
						// The /ws STOMP handshake is public (see the list below); real auth happens on the
						// STOMP CONNECT frame (see StompAuthChannelInterceptor).
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
						// child's, both enforced in FeePaymentService (assertCanPayOrRecord / listByStudent).
						// Recording a payment (POST /fee-payments) is admin-only - see further down. GET .../fee-payments/{id} (a staff receipt lookup, per
						// PaymentReceiptScreen) has no such self-check, so it stays staff-only for now.
						.requestMatchers(HttpMethod.GET, "/api/v1/fee-assessments").hasAnyRole("TEACHER", "ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/students/*/fee-assessments").hasAnyRole("TEACHER", "ADMIN", "STUDENT", "PARENT")
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
						// Admissions (applications, documents, enrolment): admin-only, every method. AdmissionService
						// re-checks the role, and scopes every lookup to the caller's school.
						.requestMatchers("/api/v1/admissions", "/api/v1/admissions/**").hasRole("ADMIN")
						// Marketing-site demo form: public by design (prospects have no account). The GET listing
						// is gated in LeadService by a static LEADS_ADMIN_TOKEN, not user roles.
						.requestMatchers(HttpMethod.POST, "/api/v1/leads").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/leads").permitAll()
						// Money, procurement and admin reporting: admin-only. The app shows none of these to
						// teachers, students or parents (PrincipalDashboardScreen hides the tiles), and several
						// post to the ledger, so a role rule here backs up the hidden UI.
						.requestMatchers("/api/v1/reports/**").hasRole("ADMIN")
						.requestMatchers("/api/v1/sponsors", "/api/v1/sponsors/**", "/api/v1/sponsorships", "/api/v1/sponsorships/**").hasRole("ADMIN")
						.requestMatchers("/api/v1/vendors", "/api/v1/vendors/**").hasRole("ADMIN")
						.requestMatchers("/api/v1/infra-expense-requests", "/api/v1/infra-expense-requests/**",
								"/api/v1/infra-expense-categories", "/api/v1/infra-expense-categories/**").hasRole("ADMIN")
						.requestMatchers("/api/v1/events/*/participation-fees", "/api/v1/events/*/collections",
								"/api/v1/events/*/balance", "/api/v1/events/*/budget", "/api/v1/events/*/expense-requests",
								"/api/v1/events/*/expense-requests/**", "/api/v1/events/*/pnl").hasRole("ADMIN")
						// Recording a fee payment marks the bill paid: staff only. Students and parents pay
						// through payment requests/attempts above, never by recording a payment themselves.
						.requestMatchers(HttpMethod.POST, "/api/v1/fee-payments").hasRole("ADMIN")
						// Roster and structure writes. Teachers add students and create sections/subjects
						// inline (ClassSectionPicker, SubjectPicker); deleting a student is admin-only, as in the app.
						.requestMatchers(HttpMethod.POST, "/api/v1/students").hasAnyRole("ADMIN", "TEACHER")
						.requestMatchers(HttpMethod.DELETE, "/api/v1/students/*").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PATCH, "/api/v1/students/*/class-section").hasAnyRole("ADMIN", "TEACHER")
						.requestMatchers(HttpMethod.POST, "/api/v1/class-sections", "/api/v1/class-sections/*/subjects",
								"/api/v1/subjects").hasAnyRole("ADMIN", "TEACHER")
						// Everything that has to work without a user login. Anything not listed here or above
						// needs a valid token for the school in X-School-Id.
						.requestMatchers("/error").permitAll()
						// Logging in, and renewing or ending a session (the access token may have expired).
						.requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/google",
								"/api/v1/auth/otp/request", "/api/v1/auth/otp/verify", "/api/v1/auth/otp/select-profile",
								"/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
						// Self-registration: the account doesn't exist yet (admin approval still gates it).
						.requestMatchers(HttpMethod.POST, "/api/v1/register/**").permitAll()
						// School picker and new-school setup, both before anyone is logged in.
						.requestMatchers(HttpMethod.GET, "/api/v1/schools").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/schools").permitAll()
						// Machines and callbacks that authenticate themselves (X-Device-Key, X-Ops-Key, OAuth state).
						.requestMatchers(HttpMethod.POST, "/api/v1/attendance/device-events").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/ops/admin-backfill").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/calls/google/callback").permitAll()
						// STOMP handshake - the login is checked on CONNECT (StompAuthChannelInterceptor).
						.requestMatchers("/ws", "/ws/**").permitAll()
						// API docs describe endpoints; they hold no school data.
						.requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
						.anyRequest().authenticated());
		return http.build();
	}

	private CorsConfigurationSource leadsCorsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(Arrays.stream(leadsAllowedOrigins.split(","))
				.map(String::trim)
				.filter(origin -> !origin.isEmpty())
				.toList());
		config.setAllowedMethods(List.of("POST", "OPTIONS"));
		config.setAllowedHeaders(List.of("Content-Type"));
		config.setAllowCredentials(false);
		config.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/v1/leads", config);
		return source;
	}

}
