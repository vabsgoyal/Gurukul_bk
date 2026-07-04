package com.gurukul.students;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StudentEnrollmentIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final String SEED_CLASS_SECTION_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void apiV1WithoutSchoolIdHeaderReturns400() throws Exception {
		mockMvc.perform(get("/api/v1/students"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Missing X-School-Id header"));
	}

	@Test
	void invalidSchoolIdHeaderReturns400() throws Exception {
		mockMvc.perform(get("/api/v1/students").header("X-School-Id", "not-a-uuid"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Invalid X-School-Id header: must be a UUID"));
	}

	@Test
	void listClassSectionsReturnsSeededRows() throws Exception {
		mockMvc.perform(get("/api/v1/class-sections").header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(2)))
				.andExpect(jsonPath("$.data[0].className").value("Grade 8"));
	}

	@Test
	void createClassSectionAndEnrollStudent() throws Exception {
		String createClassSection = """
				{
				  "className": "Grade 9",
				  "section": "A",
				  "academicYear": "2026-27"
				}
				""";

		MvcResult classSectionResult = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createClassSection))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.displayLabel").value("Grade 9 - A (2026-27)"))
				.andReturn();

		String classSectionId = JsonPath.read(
				classSectionResult.getResponse().getContentAsString(), "$.data.id");

		String enrollStudent = """
				{
				  "rollNumber": "9A-001",
				  "name": "Rahul Sharma",
				  "dob": "2012-05-15",
				  "gender": "MALE",
				  "address": "123 MG Road, Jaipur",
				  "parentName": "Rajesh Sharma",
				  "parentContact": "9876543210",
				  "classSectionId": "%s",
				  "admissionDate": "2026-04-01"
				}
				""".formatted(classSectionId);

		MvcResult studentResult = mockMvc.perform(post("/api/v1/students")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(enrollStudent))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.rollNumber").value("9A-001"))
				.andExpect(jsonPath("$.data.classSectionLabel").value("Grade 9 - A (2026-27)"))
				.andReturn();

		String studentId = JsonPath.read(studentResult.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(get("/api/v1/students/" + studentId).header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("Rahul Sharma"));

		MvcResult otherSchoolResult = mockMvc.perform(post("/api/v1/schools")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Other School",
								  "address": "1 Other Street",
								  "city": "Delhi",
								  "state": "Delhi",
								  "pincode": "110001",
								  "contactEmail": "other@school.example",
								  "contactPhone": "9000000099",
								  "principalName": "Dr. Other",
								  "directorName": "Mr. Other"
								}
								"""))
				.andExpect(status().isOk())
				.andReturn();
		String otherSchoolId = JsonPath.read(otherSchoolResult.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(get("/api/v1/students/" + studentId).header("X-School-Id", otherSchoolId))
				.andExpect(status().isNotFound());
	}

	@Test
	void enrollWithSeededClassSection() throws Exception {
		String enrollStudent = """
				{
				  "rollNumber": "8A-002",
				  "name": "Priya Singh",
				  "dob": "2012-08-20",
				  "gender": "FEMALE",
				  "address": "45 Civil Lines, Jaipur",
				  "parentName": "Anita Singh",
				  "parentContact": "9123456780",
				  "classSectionId": "%s",
				  "admissionDate": "2026-04-01"
				}
				""".formatted(SEED_CLASS_SECTION_ID);

		mockMvc.perform(post("/api/v1/students")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(enrollStudent))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.className").value("Grade 8"))
				.andExpect(jsonPath("$.data.section").value("A"));
	}

	@Test
	void missingRequiredFieldReturns400() throws Exception {
		String incomplete = """
				{
				  "rollNumber": "8A-003",
				  "name": "Test Student"
				}
				""";

		MvcResult result = mockMvc.perform(post("/api/v1/students")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(incomplete))
				.andExpect(status().isBadRequest())
				.andReturn();

		assertThat(result.getResponse().getContentAsString()).contains("dob");
	}

	@Test
	void invalidClassSectionIdReturns400() throws Exception {
		String enrollStudent = """
				{
				  "rollNumber": "8A-004",
				  "name": "Test Student",
				  "dob": "2012-05-15",
				  "gender": "MALE",
				  "address": "123 MG Road",
				  "parentName": "Parent Name",
				  "parentContact": "9876543210",
				  "classSectionId": "%s",
				  "admissionDate": "2026-04-01"
				}
				""".formatted(UUID.randomUUID());

		mockMvc.perform(post("/api/v1/students")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(enrollStudent))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Class-section not found for this school"));
	}

}
