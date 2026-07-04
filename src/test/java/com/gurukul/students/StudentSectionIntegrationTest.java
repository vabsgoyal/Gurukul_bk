package com.gurukul.students;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StudentSectionIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final String CLASS_SECTION_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
	private static final String CLASS_SECTION_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

	@Autowired
	private MockMvc mockMvc;

	private String studentId;
	private String rollNumber;

	@BeforeEach
	void enrollStudent() throws Exception {
		rollNumber = "SEC-" + UUID.randomUUID().toString().substring(0, 8);
		String enrollStudent = """
				{
				  "rollNumber": "%s",
				  "name": "Section Test Student",
				  "dob": "2012-05-15",
				  "gender": "MALE",
				  "address": "123 MG Road",
				  "parentName": "Parent Name",
				  "parentContact": "9876543210",
				  "classSectionId": "%s",
				  "admissionDate": "2026-04-01"
				}
				""".formatted(rollNumber, CLASS_SECTION_A);

		MvcResult result = mockMvc.perform(post("/api/v1/students")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(enrollStudent))
				.andExpect(status().isOk())
				.andReturn();

		studentId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
	}

	@Test
	void listStudentsByClassNameAndSection() throws Exception {
		mockMvc.perform(get("/api/v1/students/by-class-section")
						.header("X-School-Id", SCHOOL_ID)
						.param("className", "Grade 8")
						.param("section", "A")
						.param("academicYear", "2026-27"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.rollNumber == '" + rollNumber + "')]").exists());
	}

	@Test
	void listStudentsByClassSectionId() throws Exception {
		mockMvc.perform(get("/api/v1/class-sections/" + CLASS_SECTION_A + "/students")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.rollNumber == '" + rollNumber + "')]").exists());
	}

	@Test
	void transferStudentToAnotherSection() throws Exception {
		String body = """
				{
				  "classSectionId": "%s"
				}
				""".formatted(CLASS_SECTION_B);

		mockMvc.perform(patch("/api/v1/students/" + studentId + "/class-section")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.section").value("B"))
				.andExpect(jsonPath("$.message").value("Class-section updated"));

		mockMvc.perform(get("/api/v1/students/by-class-section")
						.header("X-School-Id", SCHOOL_ID)
						.param("className", "Grade 8")
						.param("section", "B")
						.param("academicYear", "2026-27"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.rollNumber == '" + rollNumber + "')]").exists());
	}

}
