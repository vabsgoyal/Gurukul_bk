package com.gurukul.students;

import com.gurukul.auth.AuthTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The app's edit forms don't send every optional field. Saving one must keep what it didn't send
 * (a student's RTE details, a teacher's email and type), and "" still clears a field on purpose.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EditsKeepUnsentFieldsIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final String SECTION = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

	/** What StudentFormScreen sends: the basics, none of the RTE fields. */
	private static final String APP_STUDENT_EDIT = """
			{"name": "RTE Student", "dob": "2013-02-02", "gender": "FEMALE", "address": "2 New Road",
			 "parentName": "Parent", "parentContact": "9811112222", "classSectionId": "%s", "admissionDate": "2026-04-01"%s}
			""";

	@Autowired private MockMvc mockMvc;

	@Test
	void editingAStudentInTheAppKeepsTheirRteDetails() throws Exception {
		String created = mockMvc.perform(post("/api/v1/students").header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "RTE Student", "dob": "2013-02-02", "gender": "FEMALE", "address": "1 Old Road",
								 "parentName": "Parent", "parentContact": "9811112222", "classSectionId": "%s",
								 "admissionDate": "2026-04-01", "sssmId": "SSSM-1", "aadhaarNumber": "123412341234",
								 "caste": "Caste", "category": "OBC", "annualIncome": 120000, "previousSchoolName": "Old School",
								 "bankAccountNumber": "5566778899", "bankIfsc": "SBIN0000001"}
								""".formatted(SECTION)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(created, "$.data.id");

		mockMvc.perform(put("/api/v1/students/" + id).header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON).content(APP_STUDENT_EDIT.formatted(SECTION, "")))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/students/" + id).header("X-School-Id", SCHOOL_ID))
				.andExpect(jsonPath("$.data.address").value("2 New Road"))
				.andExpect(jsonPath("$.data.sssmId").value("SSSM-1"))
				.andExpect(jsonPath("$.data.aadhaarNumber").value("123412341234"))
				.andExpect(jsonPath("$.data.caste").value("Caste"))
				.andExpect(jsonPath("$.data.category").value("OBC"))
				.andExpect(jsonPath("$.data.annualIncome").value(120000))
				.andExpect(jsonPath("$.data.previousSchoolName").value("Old School"))
				.andExpect(jsonPath("$.data.bankAccountNumber").value("5566778899"))
				.andExpect(jsonPath("$.data.bankIfsc").value("SBIN0000001"));

		// Sending "" still clears a field on purpose.
		mockMvc.perform(put("/api/v1/students/" + id).header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(APP_STUDENT_EDIT.formatted(SECTION, ", \"caste\": \"\", \"aadhaarNumber\": \"\"")))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/students/" + id).header("X-School-Id", SCHOOL_ID))
				.andExpect(jsonPath("$.data.caste").value(nullValue()))
				.andExpect(jsonPath("$.data.aadhaarNumber").value(nullValue()))
				.andExpect(jsonPath("$.data.sssmId").value("SSSM-1"));
	}

	@Test
	void editingATeacherInTheAppKeepsTheirEmailAndType() throws Exception {
		String created = mockMvc.perform(post("/api/v1/employees").header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Kept Teacher", "designation": "Teacher", "joinDate": "2024-04-01",
								 "contactPhone": "9822223333", "contactEmail": "kept@school.example", "employeeType": "TEACHING",
								 "bankAccount": "1234567890"}
								"""))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(created, "$.data.id");

		// What EmployeeFormScreen sends: no contactEmail, no employeeType.
		mockMvc.perform(put("/api/v1/employees/" + id).header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Kept Teacher Renamed", "designation": "Senior Teacher", "joinDate": "2024-04-01",
								 "contactPhone": "9822223333", "bankAccount": "1234567890"}
								"""))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/employees/" + id).header("X-School-Id", SCHOOL_ID))
				.andExpect(jsonPath("$.data.name").value("Kept Teacher Renamed"))
				.andExpect(jsonPath("$.data.contactEmail").value("kept@school.example"))
				.andExpect(jsonPath("$.data.employeeType").value("TEACHING"));

		mockMvc.perform(put("/api/v1/employees/" + id).header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Kept Teacher Renamed", "designation": "Senior Teacher", "joinDate": "2024-04-01",
								 "contactEmail": ""}
								"""))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/employees/" + id).header("X-School-Id", SCHOOL_ID))
				.andExpect(jsonPath("$.data.contactEmail").value(nullValue()))
				.andExpect(jsonPath("$.data.contactPhone").value("9822223333"));
	}

}
