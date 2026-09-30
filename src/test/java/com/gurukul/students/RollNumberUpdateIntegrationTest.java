package com.gurukul.students;

import com.gurukul.auth.AuthTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Roll numbers stay a gapless alphabetical rank however a student is edited, while an edit that
 * can't change the order (address, phone...) touches no classmate and logs only the edit itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RollNumberUpdateIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired private MockMvc mockMvc;
	@Autowired private JdbcTemplate jdbc;

	private String sectionId;
	private final Map<String, String> idsByName = new LinkedHashMap<>();

	@BeforeEach
	void setUp() throws Exception {
		String created = mockMvc.perform(post("/api/v1/class-sections").header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"className\": \"Grade 4\", \"section\": \"R" + UUID.randomUUID().toString().substring(0, 5)
								+ "\", \"academicYear\": \"2026-27\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		sectionId = JsonPath.read(created, "$.data.id");
		for (String name : List.of("Bela", "Chetan", "Deepa", "Esha", "Farhan")) {
			idsByName.put(name, AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, name));
		}
	}

	@Test
	void anAddressEditLeavesEveryRollNumberAndClassmateAlone() throws Exception {
		Map<String, String> before = rollNumbersByName();
		long auditBefore = studentAuditRows();

		edit(idsByName.get("Deepa"), "Deepa", "22 New Street").andExpect(status().isOk());

		assertThat(rollNumbersByName()).isEqualTo(before);
		assertThat(studentAuditRows() - auditBefore).isEqualTo(1);
	}

	@Test
	void aRenameReranksTheSectionWithoutGapsOrDuplicates() throws Exception {
		edit(idsByName.get("Farhan"), "Aarav", "1 Road").andExpect(status().isOk());
		assertThat(rollNumbersByName()).containsExactly(
				Map.entry("Aarav", "1"), Map.entry("Bela", "2"), Map.entry("Chetan", "3"),
				Map.entry("Deepa", "4"), Map.entry("Esha", "5"));

		edit(idsByName.get("Bela"), "Zoya", "1 Road").andExpect(status().isOk());
		assertThat(rollNumbersByName()).containsExactly(
				Map.entry("Aarav", "1"), Map.entry("Chetan", "2"), Map.entry("Deepa", "3"),
				Map.entry("Esha", "4"), Map.entry("Zoya", "5"));
	}

	@Test
	void renumberingClassmatesIsNotLoggedAsTheirEdit() throws Exception {
		long auditBefore = studentAuditRows();
		// Moves to the front: every classmate's rank shifts, but only the rename is someone's edit.
		edit(idsByName.get("Farhan"), "Aarav", "1 Road").andExpect(status().isOk());
		assertThat(studentAuditRows() - auditBefore).isEqualTo(1);
	}

	private org.springframework.test.web.servlet.ResultActions edit(String id, String name, String address) throws Exception {
		return mockMvc.perform(put("/api/v1/students/" + id).header("X-School-Id", SCHOOL_ID)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "%s", "dob": "2012-05-15", "gender": "MALE", "address": "%s", "parentName": "Parent",
						 "parentContact": "9876543210", "classSectionId": "%s", "admissionDate": "2026-04-01"}
						""".formatted(name, address, sectionId)));
	}

	private Map<String, String> rollNumbersByName() {
		Map<String, String> rolls = new LinkedHashMap<>();
		jdbc.query("SELECT name, roll_number FROM student WHERE class_section_id = ? ORDER BY CAST(roll_number AS INT)",
				rs -> {
					rolls.put(rs.getString("name"), rs.getString("roll_number"));
				}, UUID.fromString(sectionId));
		return rolls;
	}

	private long studentAuditRows() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE entity_type = 'Student' AND action = 'UPDATE'"
				+ " AND entity_id IN (SELECT CAST(id AS VARCHAR) FROM student WHERE class_section_id = ?)",
				Long.class, UUID.fromString(sectionId));
	}

}
