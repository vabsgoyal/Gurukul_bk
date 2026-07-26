package com.gurukul.schools;

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
class SchoolListingIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void listAllSchoolsRequiresNoHeaderAndIncludesSeedSchool() throws Exception {
		mockMvc.perform(get("/api/v1/schools"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.name == 'Gurukul Demo School')]").exists());
	}

	@Test
	void searchByPartialNameExcludesContactDetails() throws Exception {
		mockMvc.perform(get("/api/v1/schools").param("name", "Gurukul Demo"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.name == 'Gurukul Demo School')]").exists())
				.andExpect(jsonPath("$.data[0].contactEmail").doesNotExist())
				.andExpect(jsonPath("$.data[0].principalName").doesNotExist());
	}

	@Test
	void searchWithNoMatchesReturnsEmptyList() throws Exception {
		mockMvc.perform(get("/api/v1/schools").param("name", "Definitely Not A Real School Name XYZ"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(0));
	}

}
