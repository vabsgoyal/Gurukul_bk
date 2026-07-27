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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChatConversationIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void staffToStaffAndStaffToStudentConversationsAreCreatedAndIdempotent() throws Exception {
		String adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String sectionSuffix = UUID.randomUUID().toString().substring(0, 8);
		String sectionId = createSection(sectionSuffix);

		String teacher1Id = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Chat Teacher One " + sectionSuffix);
		String teacher2Id = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Chat Teacher Two " + sectionSuffix);
		String studentId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, "Chat Student " + sectionSuffix);

		String teacher1Bearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "employees", teacher1Id, "TEACHER");
		String teacher2Bearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "employees", teacher2Id, "TEACHER");

		MvcResult staffStaffResult = mockMvc.perform(post("/api/v1/chat/conversations")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + teacher1Bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"otherPartyOwnerType": "EMPLOYEE", "otherPartyOwnerId": "%s"}
								""".formatted(teacher2Id)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.type").value("STAFF_STAFF"))
				.andReturn();
		String staffStaffId = JsonPath.read(staffStaffResult.getResponse().getContentAsString(), "$.data.id");

		// Creating the same pair again is idempotent - returns the same conversation.
		MvcResult repeatResult = mockMvc.perform(post("/api/v1/chat/conversations")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + teacher1Bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"otherPartyOwnerType": "EMPLOYEE", "otherPartyOwnerId": "%s"}
								""".formatted(teacher2Id)))
				.andExpect(status().isOk())
				.andReturn();
		String repeatId = JsonPath.read(repeatResult.getResponse().getContentAsString(), "$.data.id");
		org.junit.jupiter.api.Assertions.assertEquals(staffStaffId, repeatId);

		mockMvc.perform(post("/api/v1/chat/conversations")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + teacher1Bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"otherPartyOwnerType": "STUDENT", "otherPartyOwnerId": "%s"}
								""".formatted(studentId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.type").value("STAFF_STUDENT"));

		// teacher2 is a participant of the staff-staff conversation - can read history.
		mockMvc.perform(get("/api/v1/chat/conversations/" + staffStaffId + "/messages")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + teacher2Bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.messages").isEmpty());

		// teacher1 sees the conversation in their list.
		mockMvc.perform(get("/api/v1/chat/conversations")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + teacher1Bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.id == '" + staffStaffId + "')]").exists());
	}

	@Test
	void studentToStudentConversationIsRejected() throws Exception {
		String adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String sectionSuffix = UUID.randomUUID().toString().substring(0, 8);
		String sectionId = createSection(sectionSuffix);

		String student1Id = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, "Chat Student A " + sectionSuffix);
		String student2Id = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, "Chat Student B " + sectionSuffix);
		String student1Bearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "students", student1Id, "STUDENT");

		mockMvc.perform(post("/api/v1/chat/conversations")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + student1Bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"otherPartyOwnerType": "STUDENT", "otherPartyOwnerId": "%s"}
								""".formatted(student2Id)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void nonParticipantCannotReadConversationHistory() throws Exception {
		String adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String sectionSuffix = UUID.randomUUID().toString().substring(0, 8);
		String sectionId = createSection(sectionSuffix);

		String teacher1Id = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Chat Outsider Teacher " + sectionSuffix);
		String teacher2Id = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Chat Outsider Teacher2 " + sectionSuffix);
		String studentId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, "Chat Outsider Student " + sectionSuffix);

		String teacher1Bearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "employees", teacher1Id, "TEACHER");
		AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "employees", teacher2Id, "TEACHER");
		String studentBearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "students", studentId, "STUDENT");

		MvcResult result = mockMvc.perform(post("/api/v1/chat/conversations")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + teacher1Bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"otherPartyOwnerType": "EMPLOYEE", "otherPartyOwnerId": "%s"}
								""".formatted(teacher2Id)))
				.andExpect(status().isOk())
				.andReturn();
		String conversationId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(get("/api/v1/chat/conversations/" + conversationId + "/messages")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + studentBearer))
				.andExpect(status().isForbidden());
	}

	@Test
	void botConversationGetOrCreateIsIdempotentPerCaller() throws Exception {
		String adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String sectionSuffix = UUID.randomUUID().toString().substring(0, 8);
		String sectionId = createSection(sectionSuffix);
		String studentId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, "Chat Bot Student " + sectionSuffix);
		String studentBearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "students", studentId, "STUDENT");

		MvcResult first = mockMvc.perform(post("/api/v1/chat/bot/conversation")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + studentBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.type").value("BOT"))
				.andReturn();
		String firstId = JsonPath.read(first.getResponse().getContentAsString(), "$.data.id");

		MvcResult second = mockMvc.perform(post("/api/v1/chat/bot/conversation")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + studentBearer))
				.andExpect(status().isOk())
				.andReturn();
		String secondId = JsonPath.read(second.getResponse().getContentAsString(), "$.data.id");

		org.junit.jupiter.api.Assertions.assertEquals(firstId, secondId);
	}

	private String createSection(String suffix) throws Exception {
		MvcResult sectionResult = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 5", "section": "CHAT-%s", "academicYear": "2026-27"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(sectionResult.getResponse().getContentAsString(), "$.data.id");
	}

}
