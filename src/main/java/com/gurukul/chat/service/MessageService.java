package com.gurukul.chat.service;

import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.chat.entity.Conversation;
import com.gurukul.chat.entity.Message;
import com.gurukul.chat.entity.SenderKind;
import com.gurukul.chat.repository.ConversationParticipantRepository;
import com.gurukul.chat.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
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
	private final ConversationParticipantRepository conversationParticipantRepository;

	@Transactional
	public Message send(Conversation conversation, AuthPrincipal principal, String content,
			String attachmentObjectKey, String attachmentContentType, String attachmentFileName) {
		boolean blankContent = content == null || content.isBlank();
		if (blankContent && attachmentObjectKey == null) {
			throw new IllegalArgumentException("A message needs content, an attachment, or both");
		}
		Message message = new Message();
		message.setSchoolId(conversation.getSchoolId());
		message.setConversation(conversation);
		message.setSenderKind(SenderKind.HUMAN);
		message.setSenderOwnerType(principal.getOwnerType());
		message.setSenderOwnerId(principal.getOwnerId());
		message.setContent(blankContent ? null : content);
		message.setAttachmentObjectKey(attachmentObjectKey);
		message.setAttachmentContentType(attachmentContentType);
		message.setAttachmentFileName(attachmentFileName);
		message.setSentAt(Instant.now());
		Message saved = messageRepository.save(message);
		// Replying means you've seen the chat - clears the sender's own unread count.
		conversationParticipantRepository.markRead(conversation.getId(), principal.getOwnerType(),
				principal.getOwnerId(), saved.getSentAt());
		return saved;
	}

	/** Everything in the conversation up to now counts as read for the caller. */
	@Transactional
	public void markRead(AuthPrincipal principal, UUID conversationId) {
		conversationParticipantRepository.markRead(conversationId, principal.getOwnerType(), principal.getOwnerId(),
				Instant.now());
	}

	/** Newest message per conversation - conversations with no messages are absent. */
	@Transactional(readOnly = true)
	public Map<UUID, Message> latestMessages(Collection<UUID> conversationIds) {
		if (conversationIds.isEmpty()) {
			return Map.of();
		}
		Map<UUID, Message> latest = new HashMap<>();
		for (Message message : messageRepository.findLatestIn(conversationIds)) {
			latest.putIfAbsent(message.getConversation().getId(), message);
		}
		return latest;
	}

	/** Unread count per conversation for the caller - conversations with none unread are absent. */
	@Transactional(readOnly = true)
	public Map<UUID, Long> unreadCounts(AuthPrincipal principal, Collection<UUID> conversationIds) {
		if (conversationIds.isEmpty()) {
			return Map.of();
		}
		Map<UUID, Long> counts = new HashMap<>();
		for (Object[] row : messageRepository.countUnread(principal.getOwnerType(), principal.getOwnerId(), conversationIds)) {
			counts.put((UUID) row[0], (Long) row[1]);
		}
		return counts;
	}

	@Transactional(readOnly = true)
	public long totalUnread(AuthPrincipal principal) {
		return messageRepository.countAllUnread(principal.getSchoolId(), principal.getOwnerType(), principal.getOwnerId());
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
