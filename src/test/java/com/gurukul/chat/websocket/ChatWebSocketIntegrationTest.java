package com.gurukul.chat.websocket;

import com.gurukul.auth.AuthTestSupport;
import com.gurukul.auth.security.JwtService;
import com.gurukul.config.SchoolContextFilter;
import com.gurukul.parents.ParentTestSupport;
import com.gurukul.parents.repository.ParentRepository;
import com.gurukul.parents.repository.ParentStudentLinkRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ChatWebSocketIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@LocalServerPort
	private int port;

	/** No real Expo calls from tests: a sent message pushes to the other participant's devices. */
	@MockitoBean(name = "expoPushRestClient")
	private RestClient expoPushRestClient;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private ParentRepository parentRepository;

	@Autowired
	private ParentStudentLinkRepository parentStudentLinkRepository;

	@Test
	void connectWithInvalidTokenNeverSucceeds() throws Exception {
		WebSocketStompClient client = newStompClient();
		StompHeaders connectHeaders = new StompHeaders();
		connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token");

		CompletableFuture<StompSession> future = client.connectAsync(
				wsUrl(), new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {
				});

		assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS)).isInstanceOf(Exception.class);
	}

	@Test
	void subscribeAndSendRoundTripDeliversMessageToParticipant() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String teacher1Id = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "WS Teacher One " + suffix);
		String teacher2Id = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "WS Teacher Two " + suffix);
		String teacher1Bearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "employees", teacher1Id, "TEACHER");

		MvcResult conversationResult = mockMvc.perform(post("/api/v1/chat/conversations")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + teacher1Bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"otherPartyOwnerType": "EMPLOYEE", "otherPartyOwnerId": "%s"}
								""".formatted(teacher2Id)))
				.andExpect(status().isOk())
				.andReturn();
		String conversationId = JsonPath.read(conversationResult.getResponse().getContentAsString(), "$.data.id");

		StompSession session = connect(teacher1Bearer);
		CompletableFuture<String> received = new CompletableFuture<>();
		session.subscribe("/topic/conversations/" + conversationId, new StompFrameHandlerAdapterCapturingContent(received));

		session.send("/app/conversations/" + conversationId + "/messages", java.util.Map.of("content", "Hello from the test"));

		String content = received.get(5, TimeUnit.SECONDS);
		assertThat(content).isEqualTo("Hello from the test");
		session.disconnect();
	}

	@Test
	void subscribingToAConversationYouAreNotAParticipantOfIsRejected() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String teacher1Id = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "WS Outsider One " + suffix);
		String teacher2Id = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "WS Outsider Two " + suffix);
		String outsiderId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "WS Outsider Three " + suffix);
		String teacher1Bearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "employees", teacher1Id, "TEACHER");
		String outsiderBearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "employees", outsiderId, "TEACHER");

		MvcResult conversationResult = mockMvc.perform(post("/api/v1/chat/conversations")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + teacher1Bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"otherPartyOwnerType": "EMPLOYEE", "otherPartyOwnerId": "%s"}
								""".formatted(teacher2Id)))
				.andExpect(status().isOk())
				.andReturn();
		String conversationId = JsonPath.read(conversationResult.getResponse().getContentAsString(), "$.data.id");

		StompSession outsiderSession = connect(outsiderBearer);
		outsiderSession.subscribe("/topic/conversations/" + conversationId, new StompFrameHandlerAdapterCapturingContent(null));
		// The interceptor rejects the SUBSCRIBE server-side, which closes the session - either
		// path (exception callback or disconnection) proves the subscription was not honored.
		Thread.sleep(1000);
		assertThat(outsiderSession.isConnected()).isFalse();
	}

	@Test
	void parentSubscribesAndSendsInTheirOwnConversationOnly() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String adminId = jwtService.parseToken(adminBearer).getOwnerId().toString();
		UUID parentId = ParentTestSupport.createParent(parentRepository, parentStudentLinkRepository, SCHOOL_ID, "WS Parent " + suffix);
		String parentBearer = ParentTestSupport.parentBearer(jwtService, SCHOOL_ID, parentId);

		// Any parent may reach the school's admins, linked child or not.
		MvcResult own = mockMvc.perform(post("/api/v1/chat/conversations")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + parentBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"otherPartyOwnerType": "EMPLOYEE", "otherPartyOwnerId": "%s"}
								""".formatted(adminId)))
				.andExpect(status().isOk())
				.andReturn();
		String ownId = JsonPath.read(own.getResponse().getContentAsString(), "$.data.id");

		StompSession session = connect(parentBearer);
		CompletableFuture<String> received = new CompletableFuture<>();
		session.subscribe("/topic/conversations/" + ownId, new StompFrameHandlerAdapterCapturingContent(received));
		session.send("/app/conversations/" + ownId + "/messages", java.util.Map.of("content", "Hello from a parent"));
		assertThat(received.get(5, TimeUnit.SECONDS)).isEqualTo("Hello from a parent");
		session.disconnect();

		String teacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "WS Parent Outsider " + suffix);
		MvcResult staff = mockMvc.perform(post("/api/v1/chat/conversations")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"otherPartyOwnerType": "EMPLOYEE", "otherPartyOwnerId": "%s"}
								""".formatted(teacherId)))
				.andExpect(status().isOk())
				.andReturn();
		String staffId = JsonPath.read(staff.getResponse().getContentAsString(), "$.data.id");

		StompSession snooping = connect(parentBearer);
		snooping.subscribe("/topic/conversations/" + staffId, new StompFrameHandlerAdapterCapturingContent(null));
		Thread.sleep(1000);
		assertThat(snooping.isConnected()).isFalse();
	}

	@Test
	void parentCannotSubscribeToAnotherSectionsAnnouncements() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String sectionId = ParentTestSupport.createSection(mockMvc, SCHOOL_ID, "WSP-" + suffix, "A");
		UUID parentId = ParentTestSupport.createParent(parentRepository, parentStudentLinkRepository, SCHOOL_ID, "WS Parent2 " + suffix);

		StompSession session = connect(ParentTestSupport.parentBearer(jwtService, SCHOOL_ID, parentId));
		session.subscribe("/topic/sections/" + sectionId + "/announcements", new StompFrameHandlerAdapterCapturingContent(null));
		Thread.sleep(1000);
		assertThat(session.isConnected()).isFalse();
	}

	private StompSession connect(String bearerToken) throws Exception {
		WebSocketStompClient client = newStompClient();
		StompHeaders connectHeaders = new StompHeaders();
		connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
		connectHeaders.add(SchoolContextFilter.SCHOOL_ID_HEADER, SCHOOL_ID);
		CompletableFuture<StompSession> future = client.connectAsync(
				wsUrl(), new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {
				});
		return future.get(5, TimeUnit.SECONDS);
	}

	private WebSocketStompClient newStompClient() {
		WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
		client.setMessageConverter(new MappingJackson2MessageConverter());
		return client;
	}

	private String wsUrl() {
		return "ws://localhost:" + port + "/ws/websocket";
	}

	/** Captures the "content" field of an incoming MessageResponse-shaped JSON payload. */
	private static class StompFrameHandlerAdapterCapturingContent implements org.springframework.messaging.simp.stomp.StompFrameHandler {

		private final CompletableFuture<String> target;

		StompFrameHandlerAdapterCapturingContent(CompletableFuture<String> target) {
			this.target = target;
		}

		@Override
		public Type getPayloadType(StompHeaders headers) {
			return java.util.Map.class;
		}

		@Override
		public void handleFrame(StompHeaders headers, Object payload) {
			if (target != null && payload instanceof java.util.Map<?, ?> map) {
				target.complete((String) map.get("content"));
			}
		}

	}

}
