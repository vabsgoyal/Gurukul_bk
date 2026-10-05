package com.gurukul.events;

import com.gurukul.auth.AuthTestSupport;
import com.gurukul.chat.entity.Announcement;
import com.gurukul.chat.entity.AnnouncementScope;
import com.gurukul.chat.repository.AnnouncementRepository;
import com.gurukul.parents.ParentTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Just an announcement" (participationType NONE) is the app's default event kind. It used to skip
 * scoping and the announcement entirely, so a section-scoped event went school-wide and nobody heard.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlainEventScopeIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AnnouncementRepository announcementRepository;

	private static String noneEvent(String name, String sectionId) {
		return """
				{"name": "%s", "eventDate": "2026-12-01", "category": "CULTURAL", "scope": "CLASS",
				 "sectionId": "%s", "venue": "Hall", "startAt": "2026-12-01T04:00:00Z",
				 "endAt": "2026-12-01T06:00:00Z", "participationType": "NONE"}
				""".formatted(name, sectionId);
	}

	@Test
	void noneEventWithASectionScopeIsScopedAndAnnouncedToThatSection() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String sectionId = ParentTestSupport.createSection(mockMvc, SCHOOL_ID, "Grade 7", "EVT-" + suffix);
		String name = "Class Picnic " + suffix;

		mockMvc.perform(post("/api/v1/events")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(noneEvent(name, sectionId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.scope").value("CLASS"))
				.andExpect(jsonPath("$.data.sectionId").value(sectionId));

		List<Announcement> announcements = announcementRepository.findAllBySectionIdOrderByCreatedAtDesc(UUID.fromString(sectionId));
		assertThat(announcements).anySatisfy(a -> {
			assertThat(a.getScope()).isEqualTo(AnnouncementScope.CLASS);
			assertThat(a.getTitle()).isEqualTo("New event: " + name);
		});
	}

	@Test
	void studentCannotCreateOrEditAPlainEvent() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String sectionId = ParentTestSupport.createSection(mockMvc, SCHOOL_ID, "Grade 7", "EVS-" + suffix);
		String studentId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, "Event Student " + suffix);
		String studentBearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "students", studentId, "STUDENT");

		String plain = """
				{"name": "Sneaky %s", "eventDate": "2026-12-01"}
				""".formatted(suffix);
		mockMvc.perform(post("/api/v1/events")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + studentBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(plain))
				.andExpect(status().isForbidden());

		MvcResult created = mockMvc.perform(post("/api/v1/events")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(plain))
				.andExpect(status().isOk())
				.andReturn();
		String eventId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(put("/api/v1/events/" + eventId)
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + studentBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(plain))
				.andExpect(status().isForbidden());
	}

}
