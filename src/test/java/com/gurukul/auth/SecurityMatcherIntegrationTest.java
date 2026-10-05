package com.gurukul.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the SecurityConfig matchers for the roster, academics, money and events routes: a sample of
 * each is 401 with no login and 403 for a role it isn't meant for, while the roles the app relies on
 * keep their access.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityMatcherIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final String SECTION = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
	private static final String ANY_ID = UUID.randomUUID().toString();

	@Autowired private MockMvc mockMvc;

	private String admin;
	private String teacherId;
	private String teacher;
	private String studentId;
	private String student;

	@BeforeEach
	void setUp() throws Exception {
		admin = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		teacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Matcher Teacher");
		teacher = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, admin, "employees", teacherId, "TEACHER");
		studentId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, SECTION, "Matcher Student");
		student = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, admin, "students", studentId, "STUDENT");
	}

	@Test
	void everyProtectedRouteNeedsALogin() throws Exception {
		MockHttpServletRequestBuilder[] requests = {
				// students / employees
				get("/api/v1/students"),
				get("/api/v1/students/" + studentId),
				json(put("/api/v1/students/" + studentId)),
				json(patch("/api/v1/students/" + studentId + "/class-section")),
				json(post("/api/v1/students")),
				delete("/api/v1/students/" + studentId),
				get("/api/v1/employees"),
				get("/api/v1/employees/search").param("q", "a"),
				get("/api/v1/employees/" + teacherId),
				get("/api/v1/employees/" + teacherId + "/class-sections"),
				json(post("/api/v1/employees")),
				json(put("/api/v1/employees/" + teacherId)),
				// structure
				json(post("/api/v1/class-sections")),
				json(post("/api/v1/subjects")),
				json(post("/api/v1/class-sections/" + SECTION + "/subjects")),
				// assessments / grading scale
				get("/api/v1/class-sections/" + SECTION + "/assessments"),
				get("/api/v1/assessments/" + ANY_ID),
				get("/api/v1/grading-scale"),
				// money / reporting
				get("/api/v1/reports/dues"),
				get("/api/v1/sponsors"),
				get("/api/v1/sponsorships"),
				get("/api/v1/vendors"),
				get("/api/v1/infra-expense-categories"),
				get("/api/v1/infra-expense-requests"),
				json(post("/api/v1/fee-payments")),
				// events
				get("/api/v1/events"),
				json(post("/api/v1/events")),
				json(put("/api/v1/events/" + ANY_ID)),
				delete("/api/v1/events/" + ANY_ID),
				get("/api/v1/events/" + ANY_ID + "/budget"),
				// calls
				get("/api/v1/calls/history"),
				get("/api/v1/calls/google/status")};
		for (MockHttpServletRequestBuilder request : requests) {
			mockMvc.perform(anonymous(request)).andExpect(status().isUnauthorized());
		}
	}

	@Test
	void theGoogleOAuthCallbackStaysPublic() throws Exception {
		int status = mockMvc.perform(anonymous(get("/api/v1/calls/google/callback").param("state", "x").param("code", "y")))
				.andReturn().getResponse().getStatus();
		org.junit.jupiter.api.Assertions.assertNotEquals(401, status);
		org.junit.jupiter.api.Assertions.assertNotEquals(403, status);
	}

	@Test
	void adminOnlyRoutesRejectTeachersAndStudents() throws Exception {
		MockHttpServletRequestBuilder[] adminOnly = {
				json(put("/api/v1/employees/" + teacherId)),
				json(put("/api/v1/students/" + studentId)),
				json(post("/api/v1/students/bulk")),
				json(patch("/api/v1/students/" + studentId + "/class-section")),
				delete("/api/v1/students/" + studentId),
				put("/api/v1/grading-scale").contentType(MediaType.APPLICATION_JSON).content("[]"),
				get("/api/v1/reports/fund-summary"),
				get("/api/v1/sponsors"),
				get("/api/v1/vendors"),
				get("/api/v1/infra-expense-categories"),
				get("/api/v1/events/" + ANY_ID + "/budget"),
				get("/api/v1/events/" + ANY_ID + "/collections"),
				get("/api/v1/events/" + ANY_ID + "/pnl"),
				json(post("/api/v1/fee-payments"))};
		for (String bearer : new String[] {teacher, student}) {
			for (MockHttpServletRequestBuilder request : adminOnly) {
				mockMvc.perform(as(request, bearer)).andExpect(status().isForbidden());
			}
		}
	}

	@Test
	void staffOnlyRoutesRejectStudents() throws Exception {
		MockHttpServletRequestBuilder[] staffOnly = {
				json(post("/api/v1/employees")),
				json(post("/api/v1/students")),
				json(post("/api/v1/class-sections")),
				json(post("/api/v1/subjects")),
				json(post("/api/v1/class-sections/" + SECTION + "/subjects")),
				json(post("/api/v1/class-sections/" + SECTION + "/assessments")),
				json(post("/api/v1/events")),
				json(put("/api/v1/events/" + ANY_ID)),
				delete("/api/v1/events/" + ANY_ID)};
		for (MockHttpServletRequestBuilder request : staffOnly) {
			mockMvc.perform(as(request, student)).andExpect(status().isForbidden());
		}
	}

	@Test
	void theRolesTheAppReliesOnKeepAccess() throws Exception {
		// Students read their teachers, events and the grading scale.
		mockMvc.perform(as(get("/api/v1/employees"), student)).andExpect(status().isOk());
		mockMvc.perform(as(get("/api/v1/events"), student)).andExpect(status().isOk());
		mockMvc.perform(as(get("/api/v1/grading-scale"), student)).andExpect(status().isOk());
		mockMvc.perform(as(get("/api/v1/class-sections/" + SECTION + "/assessments"), student)).andExpect(status().isOk());
		// Teachers read the roster and create events.
		mockMvc.perform(as(get("/api/v1/students"), teacher)).andExpect(status().isOk());
		mockMvc.perform(as(get("/api/v1/employees/" + teacherId + "/class-sections"), teacher)).andExpect(status().isOk());
		mockMvc.perform(as(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"Sports Day\", \"eventDate\": \"2026-12-01\"}"), teacher))
				.andExpect(status().isOk());
		// Admins edit whole records.
		mockMvc.perform(as(put("/api/v1/employees/" + teacherId).contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"Matcher Teacher\", \"designation\": \"Teacher\", \"joinDate\": \"2024-04-01\"}"), admin))
				.andExpect(status().isOk());
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request) {
		return request.contentType(MediaType.APPLICATION_JSON).content("{}");
	}

	/** Explicitly no login - DefaultTestLogin would otherwise act as the school's admin. */
	private static MockHttpServletRequestBuilder anonymous(MockHttpServletRequestBuilder request) {
		return request.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "");
	}

	private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String bearer) {
		return request.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
	}

}
