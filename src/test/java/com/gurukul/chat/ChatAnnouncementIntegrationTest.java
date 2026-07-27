package com.gurukul.chat;

import com.gurukul.auth.AuthTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
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
class ChatAnnouncementIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void adminCanPostSchoolAndClassAnnouncements() throws Exception {
		String adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String sectionId = createSection(suffix);

		mockMvc.perform(post("/api/v1/chat/announcements")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"scope": "SCHOOL", "title": "Holiday", "body": "School closed Friday"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.scope").value("SCHOOL"));

		mockMvc.perform(post("/api/v1/chat/announcements")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"scope": "CLASS", "sectionId": "%s", "title": "Field trip", "body": "Bring shoes"}
								""".formatted(sectionId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.scope").value("CLASS"));
	}

	@Test
	void onlyTheAssignedClassTeacherCanPostClassAnnouncementsAndAdminCannotSkipSchoolRestriction() throws Exception {
		String adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String sectionId = createSection(suffix);

		String classTeacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Announce Class Teacher " + suffix);
		String otherTeacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Announce Other Teacher " + suffix);

		mockMvc.perform(patch("/api/v1/class-sections/" + sectionId + "/class-teacher")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"teacherId\": \"" + classTeacherId + "\"}"))
				.andExpect(status().isOk());

		String classTeacherBearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "employees", classTeacherId, "TEACHER");
		String otherTeacherBearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "employees", otherTeacherId, "TEACHER");

		// The assigned class teacher can post to their own section.
		mockMvc.perform(post("/api/v1/chat/announcements")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + classTeacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"scope": "CLASS", "sectionId": "%s", "title": "Homework", "body": "Chapter 4"}
								""".formatted(sectionId)))
				.andExpect(status().isOk());

		// A different teacher (not this section's class teacher) cannot.
		mockMvc.perform(post("/api/v1/chat/announcements")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherTeacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"scope": "CLASS", "sectionId": "%s", "title": "Homework", "body": "Chapter 4"}
								""".formatted(sectionId)))
				.andExpect(status().isForbidden());

		// Even the class teacher cannot post a school-wide announcement.
		mockMvc.perform(post("/api/v1/chat/announcements")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + classTeacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"scope": "SCHOOL", "title": "Not allowed", "body": "..."}
								"""))
				.andExpect(status().isForbidden());
	}

	@Test
	void studentSeesSchoolAndOwnSectionAnnouncementsButNotAnotherSections() throws Exception {
		String adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String sectionId = createSection(suffix);
		String otherSectionId = createSection(suffix + "-b");

		String studentId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, "Announce Student " + suffix);
		String studentBearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "students", studentId, "STUDENT");

		MvcResult schoolAnnouncement = mockMvc.perform(post("/api/v1/chat/announcements")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"scope": "SCHOOL", "title": "Assembly", "body": "Monday 9am"}
								"""))
				.andExpect(status().isOk())
				.andReturn();
		String schoolAnnouncementId = JsonPath.read(schoolAnnouncement.getResponse().getContentAsString(), "$.data.id");

		MvcResult classAnnouncement = mockMvc.perform(post("/api/v1/chat/announcements")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"scope": "CLASS", "sectionId": "%s", "title": "Quiz", "body": "Friday"}
								""".formatted(sectionId)))
				.andExpect(status().isOk())
				.andReturn();
		String classAnnouncementId = JsonPath.read(classAnnouncement.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(get("/api/v1/chat/announcements")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + studentBearer)
						.param("sectionId", sectionId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.id == '" + schoolAnnouncementId + "')]").exists())
				.andExpect(jsonPath("$.data[?(@.id == '" + classAnnouncementId + "')]").exists());

		// The same student cannot pull announcements scoped to a section they're not enrolled in.
		mockMvc.perform(get("/api/v1/chat/announcements")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + studentBearer)
						.param("sectionId", otherSectionId))
				.andExpect(status().isForbidden());
	}

	private String createSection(String suffix) throws Exception {
		MvcResult sectionResult = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 6", "section": "ANN-%s", "academicYear": "2026-27"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(sectionResult.getResponse().getContentAsString(), "$.data.id");
	}

}
