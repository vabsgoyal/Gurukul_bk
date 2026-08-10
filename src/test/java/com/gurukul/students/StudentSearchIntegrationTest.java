package com.gurukul.students;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StudentSearchIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final String SEED_CLASS_SECTION_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

	@Autowired
	private MockMvc mockMvc;

	private void enrollStudent(String name, String parentName, String parentContact) throws Exception {
		String body = """
				{
				  "name": "%s",
				  "dob": "2012-05-15",
				  "gender": "MALE",
				  "address": "123 MG Road, Jaipur",
				  "parentName": "%s",
				  "parentContact": "%s",
				  "classSectionId": "%s",
				  "admissionDate": "2026-04-01"
				}
				""".formatted(name, parentName, parentContact, SEED_CLASS_SECTION_ID);

		mockMvc.perform(post("/api/v1/students")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk());
	}

	@Test
	void searchByExactNameReturnsMatch() throws Exception {
		enrollStudent("Ananya Gupta", "Deepak Gupta", "9811122233");

		mockMvc.perform(get("/api/v1/students/search")
						.header("X-School-Id", SCHOOL_ID)
						.param("q", "Ananya"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data[?(@.name == 'Ananya Gupta')]").exists());
	}

	@Test
	void searchToleratesTypos() throws Exception {
		enrollStudent("Vivaan Mehta", "Suresh Mehta", "9822233355");

		mockMvc.perform(get("/api/v1/students/search")
						.header("X-School-Id", SCHOOL_ID)
						.param("q", "Vivan Mehta"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.name == 'Vivaan Mehta')]").exists());
	}

	@Test
	void blankQueryReturns400() throws Exception {
		mockMvc.perform(get("/api/v1/students/search")
						.header("X-School-Id", SCHOOL_ID)
						.param("q", " "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false));
	}

	@Test
	void searchIsScopedToSchool() throws Exception {
		enrollStudent("Kabir Nair", "Ramesh Nair", "9833344455");

		String otherSchool = """
				{
				  "name": "Other School",
				  "address": "1 Other Street",
				  "city": "Delhi",
				  "state": "Delhi",
				  "pincode": "110001",
				  "contactEmail": "other-search@school.example",
				  "contactPhone": "9000000098",
				  "principalName": "Dr. Other",
				  "directorName": "Mr. Other",
				  "principalPhone": "9000000098",
				  "adminPhone": "8000000098"
				}
				""";

		var result = mockMvc.perform(post("/api/v1/schools")
						.contentType(MediaType.APPLICATION_JSON)
						.content(otherSchool))
				.andExpect(status().isOk())
				.andReturn();
		String otherSchoolId = com.jayway.jsonpath.JsonPath.read(
				result.getResponse().getContentAsString(), "$.data.school.id");

		mockMvc.perform(get("/api/v1/students/search")
						.header("X-School-Id", otherSchoolId)
						.param("q", "Kabir"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	void searchParentsMatchesByParentNameParentContactOrStudentName() throws Exception {
		enrollStudent("Ishaan Rao", "Meera Rao", "9844455566");

		mockMvc.perform(get("/api/v1/students/search-parents")
						.header("X-School-Id", SCHOOL_ID)
						.param("q", "Meera Rao"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.name == 'Ishaan Rao')]").exists());

		mockMvc.perform(get("/api/v1/students/search-parents")
						.header("X-School-Id", SCHOOL_ID)
						.param("q", "9844455566"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.name == 'Ishaan Rao')]").exists());

		mockMvc.perform(get("/api/v1/students/search-parents")
						.header("X-School-Id", SCHOOL_ID)
						.param("q", "Ishaan"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.name == 'Ishaan Rao')]").exists());
	}

	@Test
	void searchParentsBlankQueryReturns400() throws Exception {
		mockMvc.perform(get("/api/v1/students/search-parents")
						.header("X-School-Id", SCHOOL_ID)
						.param("q", ""))
				.andExpect(status().isBadRequest());
	}

}
