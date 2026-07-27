package com.gurukul.chat.bot;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.chat.bot.config.AnthropicProperties;
import com.gurukul.chat.bot.security.PrincipalContextRunner;
import com.gurukul.chat.bot.tool.BotToolRegistry;
import com.gurukul.chat.entity.Conversation;
import com.gurukul.chat.entity.ConversationType;
import com.gurukul.chat.entity.Message;
import com.gurukul.chat.entity.SenderKind;
import com.gurukul.chat.service.MessageService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotReplyServiceTest {

	private final AnthropicClient anthropicClient = mock(AnthropicClient.class);
	private final BotToolRegistry toolRegistry = mock(BotToolRegistry.class);
	private final PrincipalContextRunner principalContextRunner = mock(PrincipalContextRunner.class);
	private final MessageService messageService = mock(MessageService.class);
	private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);

	private final AuthPrincipal principal =
			new AuthPrincipal(UUID.randomUUID(), OwnerType.STUDENT, Role.STUDENT, UUID.randomUUID(), "student-1");

	private Conversation botConversation() {
		Conversation conversation = new Conversation();
		conversation.setId(UUID.randomUUID());
		conversation.setSchoolId(principal.getSchoolId());
		conversation.setType(ConversationType.BOT);
		return conversation;
	}

	private Message humanMessage(Conversation conversation) {
		Message message = new Message();
		message.setId(UUID.randomUUID());
		message.setSchoolId(conversation.getSchoolId());
		message.setConversation(conversation);
		message.setSenderKind(SenderKind.HUMAN);
		message.setSenderOwnerType(principal.getOwnerType());
		message.setSenderOwnerId(principal.getOwnerId());
		message.setContent("What's my attendance?");
		message.setSentAt(Instant.now());
		return message;
	}

	@Test
	void blankApiKeyShortCircuitsWithoutCallingAnthropic() {
		AnthropicProperties properties = new AnthropicProperties("direct", "", "claude-opus-5", "MEDIUM", 512, 5, 10);
		BotReplyService service = new BotReplyService(
				anthropicClient, properties, toolRegistry, principalContextRunner, messageService, messagingTemplate);

		Conversation conversation = botConversation();
		Message saved = new Message();
		saved.setId(UUID.randomUUID());
		saved.setSenderKind(SenderKind.BOT);
		saved.setContent("The helpdesk bot isn't configured yet - please contact your school admin.");
		when(messageService.sendBotReply(any(), anyString())).thenReturn(saved);

		service.generateReply(conversation, humanMessage(conversation), principal);

		verify(messageService).sendBotReply(conversation, "The helpdesk bot isn't configured yet - please contact your school admin.");
		verify(anthropicClient, never()).messages();
		verify(messagingTemplate).convertAndSend(
				org.mockito.ArgumentMatchers.eq("/topic/conversations/" + conversation.getId()),
				org.mockito.ArgumentMatchers.any(com.gurukul.chat.dto.ChatDtos.MessageResponse.class));
	}

	@Test
	void endTurnResponseIsPersistedAndBroadcastVerbatim() {
		AnthropicProperties properties = new AnthropicProperties("direct", "test-key", "claude-opus-5", "MEDIUM", 512, 5, 10);
		BotReplyService service = new BotReplyService(
				anthropicClient, properties, toolRegistry, principalContextRunner, messageService, messagingTemplate);

		Conversation conversation = botConversation();
		Page<Message> history = new PageImpl<>(List.of(humanMessage(conversation)));
		when(messageService.history(any(), any())).thenReturn(history);
		when(toolRegistry.toolsFor(principal)).thenReturn(List.of());

		com.anthropic.models.messages.Message anthropicResponse = mock(com.anthropic.models.messages.Message.class);
		TextBlock textBlock = mock(TextBlock.class);
		when(textBlock.text()).thenReturn("You were present today.");
		ContentBlock contentBlock = mock(ContentBlock.class);
		when(contentBlock.isText()).thenReturn(true);
		when(contentBlock.asText()).thenReturn(textBlock);
		when(anthropicResponse.content()).thenReturn(List.of(contentBlock));
		when(anthropicResponse.stopReason()).thenReturn(Optional.of(StopReason.END_TURN));

		com.anthropic.services.blocking.MessageService anthropicMessagesApi =
				mock(com.anthropic.services.blocking.MessageService.class);
		when(anthropicMessagesApi.create(any(com.anthropic.models.messages.MessageCreateParams.class)))
				.thenReturn(anthropicResponse);
		when(anthropicClient.messages()).thenReturn(anthropicMessagesApi);

		Message saved = new Message();
		saved.setId(UUID.randomUUID());
		saved.setSenderKind(SenderKind.BOT);
		saved.setContent("You were present today.");
		when(messageService.sendBotReply(any(), anyString())).thenReturn(saved);

		service.generateReply(conversation, humanMessage(conversation), principal);

		verify(messageService).sendBotReply(conversation, "You were present today.");
		assertThat(saved.getContent()).isEqualTo("You were present today.");
	}

}
