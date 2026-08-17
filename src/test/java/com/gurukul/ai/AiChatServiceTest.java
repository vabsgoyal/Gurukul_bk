package com.gurukul.ai;

import com.gurukul.ai.config.OpenRouterProperties;
import com.gurukul.ai.dto.AiDtos.AiChatMessage;
import com.gurukul.ai.dto.AiDtos.AiChatRequest;
import com.gurukul.ai.provider.AiProvider;
import com.gurukul.ai.service.AiChatService;
import com.gurukul.ai.service.AiRateLimiter;
import com.gurukul.ai.service.AiUnavailableException;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthPrincipal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatServiceTest {

	private final AiProvider provider = mock(AiProvider.class);
	private final AiRateLimiter rateLimiter = mock(AiRateLimiter.class);

	private OpenRouterProperties properties(int historyWindow) {
		return new OpenRouterProperties(
				"https://example.test/v1", "test-key", "test/model",
				1500, 0.4, 60, "https://example.test", "Test", historyWindow, 40, 60000);
	}

	private AiChatService service(int historyWindow) {
		return new AiChatService(provider, rateLimiter, properties(historyWindow));
	}

	private AuthPrincipal principal(Role role) {
		OwnerType ownerType = role == Role.STUDENT ? OwnerType.STUDENT : OwnerType.EMPLOYEE;
		return new AuthPrincipal(UUID.randomUUID(), ownerType, role, UUID.randomUUID(), "u");
	}

	private AiChatRequest request(String... contents) {
		AiChatRequest request = new AiChatRequest();
		request.setMessages(java.util.Arrays.stream(contents).map(content -> {
			AiChatMessage message = new AiChatMessage();
			message.setRole("user");
			message.setContent(content);
			return message;
		}).toList());
		return request;
	}

	/**
	 * Uses a fresh mock per call so tests that compare two roles' prompts don't accumulate
	 * invocations on a shared mock and trip the verify().
	 */
	private String systemPromptFor(Role role) {
		AiProvider isolated = mock(AiProvider.class);
		when(isolated.isConfigured()).thenReturn(true);
		when(isolated.modelId()).thenReturn("test/model");
		when(isolated.complete(anyString(), anyList())).thenReturn("ok");

		new AiChatService(isolated, rateLimiter, properties(20)).chat(principal(role), request("hello"));

		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(isolated).complete(captor.capture(), anyList());
		return captor.getValue();
	}

	/**
	 * The prompt is chosen from the JWT role and nothing else. This is the security property that
	 * replaced the client-side student/teacher toggle: a student picking "teacher mode" in the UI
	 * could previously ask for a complete answer key to their own homework.
	 */
	@Test
	void studentGetsATutoringPromptThatWithholdsAnswersToGradedWork() {
		assertThat(systemPromptFor(Role.STUDENT))
				.contains("tutor")
				.contains("Do not simply hand over a final answer to copy");
	}

	@Test
	void teacherGetsAPromptThatExplicitlyPermitsAnswerKeys() {
		assertThat(systemPromptFor(Role.TEACHER))
				.contains("teaching assistant")
				.contains("Complete answers and full marking schemes are appropriate here");
	}

	/** An admin doing lesson planning is doing a teacher's job, not studying. */
	@Test
	void adminGetsTheTeacherPrompt() {
		assertThat(systemPromptFor(Role.ADMIN))
				.isEqualTo(systemPromptFor(Role.TEACHER));
	}

	@Test
	void parentGetsItsOwnPromptRatherThanTheStudentOrTeacherOne() {
		String parent = systemPromptFor(Role.PARENT);
		assertThat(parent).contains("parent");
		assertThat(parent).isNotEqualTo(systemPromptFor(Role.STUDENT));
		assertThat(parent).isNotEqualTo(systemPromptFor(Role.TEACHER));
	}

	/** Every role must be told it cannot see school records, or it will invent marks and fees. */
	@Test
	void everyRolePromptForbidsInventingSchoolRecords() {
		for (Role role : Role.values()) {
			assertThat(systemPromptFor(role))
					.as("prompt for %s", role)
					.contains("no access to");
		}
	}

	/**
	 * The app renders replies in a plain Text component with no Markdown or LaTeX renderer, so
	 * every role's prompt must carry the plain-text rules or users see raw "**bold**" and
	 * "\\frac{a}{b}" in the chat bubble.
	 */
	@Test
	void everyRolePromptForbidsMarkdownAndLatex() {
		for (Role role : Role.values()) {
			assertThat(systemPromptFor(role))
					.as("formatting rules for %s", role)
					.contains("No Markdown")
					.contains("No LaTeX");
		}
	}

	@Test
	void unconfiguredProviderIsReportedGracefullyAndNeverReachesTheProvider() {
		when(provider.isConfigured()).thenReturn(false);

		assertThatThrownBy(() -> service(20).chat(principal(Role.STUDENT), request("hi")))
				.isInstanceOf(AiUnavailableException.class)
				.hasMessageContaining("school admin");

		verify(provider, never()).complete(anyString(), anyList());
	}

	/**
	 * The rate limiter must run before the provider call, otherwise the cap wouldn't actually stop
	 * anything being billed.
	 */
	@Test
	void rateLimitRejectionPreventsTheProviderCall() {
		when(provider.isConfigured()).thenReturn(true);
		org.mockito.Mockito.doThrow(new AiUnavailableException("hourly limit"))
				.when(rateLimiter).checkAndRecord(any());

		assertThatThrownBy(() -> service(20).chat(principal(Role.STUDENT), request("hi")))
				.isInstanceOf(AiUnavailableException.class);

		verify(provider, never()).complete(anyString(), anyList());
	}

	/**
	 * Trimming keeps the NEWEST turns. Dropping them to preserve older context would answer a
	 * question the user has already moved on from.
	 */
	@Test
	void historyIsTrimmedToTheWindowKeepingTheMostRecentTurns() {
		when(provider.isConfigured()).thenReturn(true);
		when(provider.modelId()).thenReturn("test/model");
		when(provider.complete(anyString(), anyList())).thenReturn("ok");

		String[] contents = IntStream.rangeClosed(1, 10).mapToObj(i -> "msg" + i).toArray(String[]::new);
		service(3).chat(principal(Role.STUDENT), request(contents));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<AiProvider.ChatTurn>> captor = ArgumentCaptor.forClass(List.class);
		verify(provider).complete(anyString(), captor.capture());

		assertThat(captor.getValue()).extracting(AiProvider.ChatTurn::content)
				.containsExactly("msg8", "msg9", "msg10");
	}

	@Test
	void shortHistoryIsPassedThroughUntouched() {
		when(provider.isConfigured()).thenReturn(true);
		when(provider.modelId()).thenReturn("test/model");
		when(provider.complete(anyString(), anyList())).thenReturn("ok");

		service(20).chat(principal(Role.STUDENT), request("a", "b"));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<AiProvider.ChatTurn>> captor = ArgumentCaptor.forClass(List.class);
		verify(provider).complete(anyString(), captor.capture());
		assertThat(captor.getValue()).hasSize(2);
	}

	@Test
	void responseCarriesTheReplyAndTheModelThatWasConfigured() {
		when(provider.isConfigured()).thenReturn(true);
		when(provider.modelId()).thenReturn("vendor/some-model");
		when(provider.complete(anyString(), anyList())).thenReturn("the answer");

		var response = service(20).chat(principal(Role.TEACHER), request("q"));

		assertThat(response.getReply()).isEqualTo("the answer");
		assertThat(response.getModel()).isEqualTo("vendor/some-model");
	}

}
