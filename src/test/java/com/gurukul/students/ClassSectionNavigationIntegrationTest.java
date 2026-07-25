package com.gurukul.students;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClassSectionNavigationIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final String CLASS_SECTION_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void listDistinctClasses() throws Exception {
		mockMvc.perform(get("/api/v1/class-sections/classes")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@ == 'Grade 8')]").exists());
	}

	@Test
	void listSectionsWithinAClass() throws Exception {
		mockMvc.perform(get("/api/v1/class-sections/by-class")
						.header("X-School-Id", SCHOOL_ID)
						.param("className", "Grade 8"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.section == 'A')]").exists())
				.andExpect(jsonPath("$.data[?(@.section == 'B')]").exists());
	}

	@Test
	void getClassSectionById() throws Exception {
		mockMvc.perform(get("/api/v1/class-sections/" + CLASS_SECTION_A)
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.className").value("Grade 8"))
				.andExpect(jsonPath("$.data.section").value("A"));
	}

}
