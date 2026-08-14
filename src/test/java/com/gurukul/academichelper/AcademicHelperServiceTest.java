package com.gurukul.academichelper;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.gurukul.academichelper.AcademicHelperDtos.AskRequest;
import com.gurukul.academichelper.AcademicHelperDtos.ChatMessageDto;
import com.gurukul.chat.bot.config.AnthropicProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AcademicHelperServiceTest {

	private final AnthropicClient anthropicClient = mock(AnthropicClient.class);

	@Test
	void notConfiguredThrowsWithoutCallingAnthropic() {
		AnthropicProperties properties = new AnthropicProperties("direct", "", "claude-opus-5", "MEDIUM", 512, 5, 10);
		AcademicHelperService service = new AcademicHelperService(anthropicClient, properties);

		AskRequest request = new AskRequest("student", List.of(new ChatMessageDto("user", "What is 2+2?")));

		assertThatThrownBy(() -> service.ask(request))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("isn't configured yet");
		verify(anthropicClient, never()).messages();
	}

	@Test
	void studentModeReturnsAssistantTextAndUsesStudentSystemPrompt() {
		AnthropicProperties properties = new AnthropicProperties("direct", "test-key", "claude-opus-5", "MEDIUM", 512, 5, 10);
		AcademicHelperService service = new AcademicHelperService(anthropicClient, properties);

		com.anthropic.models.messages.Message anthropicResponse = mock(com.anthropic.models.messages.Message.class);
		TextBlock textBlock = mock(TextBlock.class);
		when(textBlock.text()).thenReturn("2+2 is 4.");
		ContentBlock contentBlock = mock(ContentBlock.class);
		when(contentBlock.isText()).thenReturn(true);
		when(contentBlock.asText()).thenReturn(textBlock);
		when(anthropicResponse.content()).thenReturn(List.of(contentBlock));

		com.anthropic.services.blocking.MessageService anthropicMessagesApi =
				mock(com.anthropic.services.blocking.MessageService.class);
		when(anthropicMessagesApi.create(any(MessageCreateParams.class))).thenReturn(anthropicResponse);
		when(anthropicClient.messages()).thenReturn(anthropicMessagesApi);

		AskRequest request = new AskRequest("student", List.of(new ChatMessageDto("user", "What is 2+2?")));

		String reply = service.ask(request);

		assertThat(reply).isEqualTo("2+2 is 4.");
	}

	@Test
	void rateLimitIsTranslatedToFriendlyMessage() {
		AnthropicProperties properties = new AnthropicProperties("direct", "test-key", "claude-opus-5", "MEDIUM", 512, 5, 10);
		AcademicHelperService service = new AcademicHelperService(anthropicClient, properties);

		com.anthropic.services.blocking.MessageService anthropicMessagesApi =
				mock(com.anthropic.services.blocking.MessageService.class);
		when(anthropicMessagesApi.create(any(MessageCreateParams.class)))
				.thenThrow(mock(RateLimitException.class));
		when(anthropicClient.messages()).thenReturn(anthropicMessagesApi);

		AskRequest request = new AskRequest("teacher", List.of(new ChatMessageDto("user", "Plan a lesson")));

		assertThatThrownBy(() -> service.ask(request))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("try again in a moment");
	}

}
