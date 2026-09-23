package com.gurukul.schools;

import com.gurukul.schools.entity.School;
import com.gurukul.schools.repository.SchoolRepository;
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
class SchoolListingIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SchoolRepository schoolRepository;

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

	@Test
	void inactiveSchoolIsHiddenFromDirectoryAndSearch() throws Exception {
		School school = saveSchool("Inactive Listing School ABC", false);

		mockMvc.perform(get("/api/v1/schools"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.id == '" + school.getId() + "')]").doesNotExist());
		mockMvc.perform(get("/api/v1/schools").param("name", "Inactive Listing School ABC"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	void inactiveSchoolRejectsTenantScopedRequests() throws Exception {
		School school = saveSchool("Inactive Login School ABC", false);

		mockMvc.perform(post("/api/v1/auth/login")
						.header("X-School-Id", school.getId().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"admin\",\"password\":\"whatever\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("School not found"));
	}

	@Test
	void activeSchoolIsListed() throws Exception {
		School school = saveSchool("Active Listing School ABC", true);

		mockMvc.perform(get("/api/v1/schools").param("name", "Active Listing School ABC"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].id").value(school.getId().toString()));
	}

	private School saveSchool(String name, boolean active) {
		School school = new School();
		school.setName(name);
		school.setAddress("1 Test Road");
		school.setCity("Jaipur");
		school.setState("Rajasthan");
		school.setPincode("302001");
		school.setContactEmail("test@example.com");
		school.setContactPhone("9999999999");
		school.setPrincipalName("Principal");
		school.setDirectorName("Director");
		school.setActive(active);
		return schoolRepository.save(school);
	}

}
