package com.gurukul.chat.service;

import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.chat.entity.Conversation;
import com.gurukul.chat.entity.Message;
import com.gurukul.chat.entity.SenderKind;
import com.gurukul.chat.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Takes an explicit AuthPrincipal, never AuthContext.current()/SchoolContext - see ConversationService.
 * Trusts that the caller has already validated participancy (via
 * ConversationService.requireParticipant) before calling {@link #send}; does not re-validate here.
 */
@Service
@RequiredArgsConstructor
public class MessageService {

	private final MessageRepository messageRepository;

	@Transactional
	public Message send(Conversation conversation, AuthPrincipal principal, String content) {
		Message message = new Message();
		message.setSchoolId(conversation.getSchoolId());
		message.setConversation(conversation);
		message.setSenderKind(SenderKind.HUMAN);
		message.setSenderOwnerType(principal.getOwnerType());
		message.setSenderOwnerId(principal.getOwnerId());
		message.setContent(content);
		message.setSentAt(Instant.now());
		return messageRepository.save(message);
	}

	/**
	 * Persists a bot-authored reply. senderOwnerType/senderOwnerId stay null (SenderKind.BOT).
	 */
	@Transactional
	public Message sendBotReply(Conversation conversation, String content) {
		Message message = new Message();
		message.setSchoolId(conversation.getSchoolId());
		message.setConversation(conversation);
		message.setSenderKind(SenderKind.BOT);
		message.setContent(content);
		message.setSentAt(Instant.now());
		return messageRepository.save(message);
	}

	public Page<Message> history(UUID conversationId, Pageable pageable) {
		return messageRepository.findAllByConversation_IdOrderBySentAtDesc(conversationId, pageable);
	}

}
