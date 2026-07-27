package com.gurukul.employees;

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
class EmployeeSearchIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	private void createEmployee(String name, String designation) throws Exception {
		String body = """
				{
				  "name": "%s",
				  "designation": "%s",
				  "joinDate": "2024-04-01",
				  "contactPhone": "9876500000"
				}
				""".formatted(name, designation);

		mockMvc.perform(post("/api/v1/employees")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk());
	}

	@Test
	void searchByNameReturnsMatch() throws Exception {
		createEmployee("Kavita Joshi", "Teacher");

		mockMvc.perform(get("/api/v1/employees/search")
						.header("X-School-Id", SCHOOL_ID)
						.param("q", "Kavita"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data[?(@.name == 'Kavita Joshi')]").exists());
	}

	@Test
	void searchToleratesTypos() throws Exception {
		createEmployee("Rohit Bansal", "Principal");

		mockMvc.perform(get("/api/v1/employees/search")
						.header("X-School-Id", SCHOOL_ID)
						.param("q", "Rohit Bansl"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.name == 'Rohit Bansal')]").exists());
	}

	@Test
	void blankQueryReturns400() throws Exception {
		mockMvc.perform(get("/api/v1/employees/search")
						.header("X-School-Id", SCHOOL_ID)
						.param("q", " "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false));
	}

	@Test
	void searchIsScopedToSchool() throws Exception {
		createEmployee("Neha Kapoor", "Accountant");

		String otherSchool = """
				{
				  "name": "Other School Employees",
				  "address": "1 Other Street",
				  "city": "Delhi",
				  "state": "Delhi",
				  "pincode": "110001",
				  "contactEmail": "other-emp-search@school.example",
				  "contactPhone": "9000000097",
				  "principalName": "Dr. Other",
				  "directorName": "Mr. Other"
				}
				""";

		var result = mockMvc.perform(post("/api/v1/schools")
						.contentType(MediaType.APPLICATION_JSON)
						.content(otherSchool))
				.andExpect(status().isOk())
				.andReturn();
		String otherSchoolId = com.jayway.jsonpath.JsonPath.read(
				result.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(get("/api/v1/employees/search")
						.header("X-School-Id", otherSchoolId)
						.param("q", "Neha"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(0));
	}

}
