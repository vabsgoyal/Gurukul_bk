package com.gurukul.chat.websocket;

import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.chat.bot.BotReplyService;
import com.gurukul.chat.dto.ChatDtos.MessageResponse;
import com.gurukul.chat.dto.ChatDtos.SendMessageRequest;
import com.gurukul.chat.entity.Conversation;
import com.gurukul.chat.entity.ConversationType;
import com.gurukul.chat.entity.Message;
import com.gurukul.chat.entity.ConversationParticipant;
import com.gurukul.chat.repository.ConversationParticipantRepository;
import com.gurukul.chat.service.AttachmentService;
import com.gurukul.chat.service.ConversationService;
import com.gurukul.chat.service.MessageService;
import com.gurukul.notifications.service.PushNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Live send path for 1:1 (and BOT) conversations. Client publishes to
 * /app/conversations/{conversationId}/messages; the server always broadcasts the persisted,
 * server-assigned-id canonical message to /topic/conversations/{conversationId} - this is the
 * only write path for messages (REST is read-only for conversations/messages; see
 * ConversationController).
 */
@Controller
@RequiredArgsConstructor
public class ChatMessageController {

	private final ConversationService conversationService;
	private final MessageService messageService;
	private final AttachmentService attachmentService;
	private final SimpMessagingTemplate messagingTemplate;
	private final BotReplyService botReplyService;
	private final ConversationParticipantRepository conversationParticipantRepository;
	private final PushNotificationService pushNotificationService;

	@MessageMapping("/conversations/{conversationId}/messages")
	public void send(
			@DestinationVariable UUID conversationId,
			@Payload SendMessageRequest request,
			SimpMessageHeaderAccessor accessor) {
		AuthPrincipal principal = requirePrincipal(accessor);
		Conversation conversation = conversationService.requireParticipant(principal, conversationId);
		Message saved = messageService.send(conversation, principal, request.getContent(),
				request.getAttachmentObjectKey(), request.getAttachmentContentType(), request.getAttachmentFileName());
		String attachmentUrl = attachmentService.presignDownload(saved.getAttachmentObjectKey());
		messagingTemplate.convertAndSend(
				"/topic/conversations/" + conversationId, MessageResponse.from(saved, attachmentUrl));
		if (conversation.getType() == ConversationType.BOT) {
			botReplyService.generateReply(conversation, saved, principal);
		} else {
			notifyOtherParticipants(conversation, principal, saved);
		}
	}

	/** BOT conversations have no other human participant to notify - see Conversation's javadoc. */
	private void notifyOtherParticipants(Conversation conversation, AuthPrincipal sender, Message saved) {
		List<PushNotificationService.Recipient> recipients = conversationParticipantRepository
				.findAllByConversation_Id(conversation.getId()).stream()
				.filter(p -> !(p.getOwnerType() == sender.getOwnerType() && p.getOwnerId().equals(sender.getOwnerId())))
				.map(p -> new PushNotificationService.Recipient(p.getOwnerType(), p.getOwnerId()))
				.toList();
		String preview = saved.getContent() != null ? saved.getContent() : "Sent an attachment";
		pushNotificationService.sendToRecipients(conversation.getSchoolId(), recipients, "New message", preview,
				Map.of("type", "NEW_MESSAGE", "conversationId", String.valueOf(conversation.getId())));
	}

	private AuthPrincipal requirePrincipal(SimpMessageHeaderAccessor accessor) {
		if (!(accessor.getUser() instanceof StompPrincipal stompPrincipal)) {
			throw new AccessDeniedException("Not authenticated");
		}
		return stompPrincipal.getAuthPrincipal();
	}

}
