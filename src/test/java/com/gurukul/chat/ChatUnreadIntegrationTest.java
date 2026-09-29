package com.gurukul.chat;

import com.gurukul.auth.AuthTestSupport;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.chat.entity.Conversation;
import com.gurukul.chat.repository.ConversationRepository;
import com.gurukul.chat.service.MessageService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The chat list carries each conversation's last message and the caller's unread count, tracked server-side. */
@SpringBootTest
@AutoConfigureMockMvc
class ChatUnreadIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired private MockMvc mockMvc;
	@Autowired private MessageService messageService;
	@Autowired private ConversationRepository conversationRepository;

	private String aliceBearer;
	private String bobBearer;
	private AuthPrincipal alice;
	private AuthPrincipal bob;
	private String aliceId;
	private String bobId;

	@BeforeEach
	void setUp() throws Exception {
		String admin = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		aliceId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Unread Alice " + suffix);
		bobId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Unread Bob " + suffix);
		aliceBearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, admin, "employees", aliceId, "TEACHER");
		bobBearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, admin, "employees", bobId, "TEACHER");
		alice = principal(aliceId);
		bob = principal(bobId);
	}

	@Test
	void unreadCountsFollowMessagesAndReadMarks() throws Exception {
		Conversation chat = conversationWith(aliceBearer, bobId);
		messageService.send(chat, alice, "First", null, null, null);
		messageService.send(chat, alice, "Second", null, null, null);

		list(bobBearer)
				.andExpect(jsonPath("$.data[0].id").value(chat.getId().toString()))
				.andExpect(jsonPath("$.data[0].unreadCount").value(2))
				.andExpect(jsonPath("$.data[0].lastMessage.content").value("Second"))
				.andExpect(jsonPath("$.data[0].lastMessage.senderOwnerId").value(aliceId));
		totalUnread(bobBearer).andExpect(jsonPath("$.data.unread").value(2));
		// Your own messages are never unread for you.
		list(aliceBearer).andExpect(jsonPath("$.data[0].unreadCount").value(0));

		markRead(bobBearer, chat).andExpect(status().isOk());
		list(bobBearer).andExpect(jsonPath("$.data[0].unreadCount").value(0));
		totalUnread(bobBearer).andExpect(jsonPath("$.data.unread").value(0));

		// Replying marks the chat read for the sender, and is unread for the other side.
		messageService.send(chat, alice, "Third", null, null, null);
		messageService.send(chat, bob, "Reply", null, null, null);
		list(bobBearer).andExpect(jsonPath("$.data[0].unreadCount").value(0));
		list(aliceBearer)
				.andExpect(jsonPath("$.data[0].unreadCount").value(1))
				.andExpect(jsonPath("$.data[0].lastMessage.content").value("Reply"));
	}

	@Test
	void theChatWithTheNewestMessageComesFirst() throws Exception {
		String carolId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Unread Carol");
		Conversation withBob = conversationWith(aliceBearer, bobId);
		Conversation withCarol = conversationWith(aliceBearer, carolId);
		messageService.send(withCarol, alice, "To Carol", null, null, null);
		messageService.send(withBob, alice, "To Bob, later", null, null, null);

		list(aliceBearer)
				.andExpect(jsonPath("$.data[0].id").value(withBob.getId().toString()))
				.andExpect(jsonPath("$.data[1].id").value(withCarol.getId().toString()));
	}

	@Test
	void onlyParticipantsCanMarkAChatRead() throws Exception {
		String carolId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Unread Outsider");
		String admin = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String carolBearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, admin, "employees", carolId, "TEACHER");
		Conversation chat = conversationWith(aliceBearer, bobId);

		markRead(carolBearer, chat).andExpect(status().is4xxClientError());
	}

	private Conversation conversationWith(String bearer, String otherEmployeeId) throws Exception {
		String response = mockMvc.perform(post("/api/v1/chat/conversations")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"otherPartyOwnerType": "EMPLOYEE", "otherPartyOwnerId": "%s"}
								""".formatted(otherEmployeeId)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return conversationRepository.findById(UUID.fromString(JsonPath.read(response, "$.data.id"))).orElseThrow();
	}

	private ResultActions list(String bearer) throws Exception {
		return mockMvc.perform(get("/api/v1/chat/conversations")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer))
				.andExpect(status().isOk());
	}

	private ResultActions totalUnread(String bearer) throws Exception {
		return mockMvc.perform(get("/api/v1/chat/unread-count")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer))
				.andExpect(status().isOk());
	}

	private ResultActions markRead(String bearer, Conversation chat) throws Exception {
		return mockMvc.perform(post("/api/v1/chat/conversations/" + chat.getId() + "/read")
				.header("X-School-Id", SCHOOL_ID)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer));
	}

	private static AuthPrincipal principal(String employeeId) {
		return new AuthPrincipal(UUID.fromString(employeeId), OwnerType.EMPLOYEE, Role.TEACHER,
				UUID.fromString(SCHOOL_ID), "teacher");
	}

}
