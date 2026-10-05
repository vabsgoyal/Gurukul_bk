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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Money, procurement, reports and roster deletes are admin-only; teachers keep the inline roster
 * and structure writes the app gives them; students and parents get neither.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminOnlyRulesIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final String SECTION = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

	@Autowired private MockMvc mockMvc;

	private String admin;
	private String teacher;
	private String student;
	private String studentId;

	@BeforeEach
	void setUp() throws Exception {
		admin = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String teacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Rules Teacher");
		teacher = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, admin, "employees", teacherId, "TEACHER");
		studentId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, SECTION, "Rules Student");
		student = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, admin, "students", studentId, "STUDENT");
	}

	@Test
	void moneyProcurementAndReportsAreAdminOnly() throws Exception {
		String anyEvent = UUID.randomUUID().toString();
		MockHttpServletRequestBuilder[] adminOnly = {
				get("/api/v1/reports/dues"),
				get("/api/v1/reports/fund-summary"),
				get("/api/v1/reports/payroll/overview"),
				get("/api/v1/sponsorships"),
				post("/api/v1/sponsors").contentType(MediaType.APPLICATION_JSON).content("{}"),
				get("/api/v1/vendors"),
				get("/api/v1/infra-expense-requests"),
				get("/api/v1/events/" + anyEvent + "/pnl"),
				post("/api/v1/events/" + anyEvent + "/collections").contentType(MediaType.APPLICATION_JSON).content("{}"),
				post("/api/v1/fee-payments").contentType(MediaType.APPLICATION_JSON).content("{}"),
				delete("/api/v1/students/" + studentId)};
		for (String bearer : new String[] {teacher, student}) {
			for (MockHttpServletRequestBuilder request : adminOnly) {
				mockMvc.perform(as(request, bearer)).andExpect(status().isForbidden());
			}
		}
		mockMvc.perform(as(get("/api/v1/reports/dues"), admin)).andExpect(status().isOk());
	}

	@Test
	void teachersKeepInlineRosterWritesAndStudentsDoNot() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 6);
		mockMvc.perform(as(post("/api/v1/class-sections").contentType(MediaType.APPLICATION_JSON)
						.content("{\"className\": \"Grade 9\", \"section\": \"T" + suffix + "\", \"academicYear\": \"2026-27\"}"), teacher))
				.andExpect(status().isOk());
		mockMvc.perform(as(post("/api/v1/subjects").contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\": \"T" + suffix + "\", \"name\": \"Teacher Subject\"}"), teacher))
				.andExpect(status().isOk());

		mockMvc.perform(as(post("/api/v1/class-sections").contentType(MediaType.APPLICATION_JSON)
						.content("{\"className\": \"Grade 9\", \"section\": \"S" + suffix + "\", \"academicYear\": \"2026-27\"}"), student))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(post("/api/v1/subjects").contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\": \"S" + suffix + "\", \"name\": \"Student Subject\"}"), student))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(post("/api/v1/students").contentType(MediaType.APPLICATION_JSON).content("{}"), student))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminsAndTeachersAddStudentsButOnlyAnAdminGetsTheSensitiveFieldsBack() throws Exception {
		String body = """
				{"name": "%s", "dob": "2013-01-01", "gender": "FEMALE", "address": "1 Road",
				 "parentName": "Parent", "parentContact": "%s", "classSectionId": "%s",
				 "admissionDate": "2026-04-01", "aadhaarNumber": "123412341234", "bankAccountNumber": "5566778899"}
				""";
		mockMvc.perform(as(post("/api/v1/students").contentType(MediaType.APPLICATION_JSON)
						.content(body.formatted("Not Added", "9876512340", SECTION)), student))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/students").header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "")
						.contentType(MediaType.APPLICATION_JSON).content(body.formatted("Not Added", "9876512341", SECTION)))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(as(post("/api/v1/students").contentType(MediaType.APPLICATION_JSON)
						.content(body.formatted("Teacher Added", "9876512342", SECTION)), teacher))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").isNotEmpty())
				.andExpect(jsonPath("$.data.registrationNumber").doesNotExist())
				.andExpect(jsonPath("$.data.aadhaarNumber").doesNotExist())
				.andExpect(jsonPath("$.data.bankAccountNumber").doesNotExist());

		mockMvc.perform(as(post("/api/v1/students").contentType(MediaType.APPLICATION_JSON)
						.content(body.formatted("Admin Added", "9876512345", SECTION)), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.registrationNumber").isNotEmpty())
				.andExpect(jsonPath("$.data.aadhaarNumber").value("123412341234"))
				.andExpect(jsonPath("$.data.bankAccountNumber").value("5566778899"));
	}

	@Test
	void aUserCanOnlyReadTheirOwnSchoolsProfile() throws Exception {
		mockMvc.perform(get("/api/v1/schools/" + SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "Bearer " + teacher))
				.andExpect(status().isOk());
		// No X-School-Id, a different school's id in the path: the token's school doesn't match.
		mockMvc.perform(get("/api/v1/schools/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isForbidden());
	}

	private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String bearer) {
		return request.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
	}

}
