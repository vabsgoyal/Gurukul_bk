package com.gurukul.chat.repository;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.chat.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

	/**
	 * Reused for both REST paginated history (large page sizes) and the bot's bounded
	 * conversation-history window (small page sizes) - same query, different Pageable.
	 */
	Page<Message> findAllByConversation_IdOrderBySentAtDesc(UUID conversationId, Pageable pageable);

	/** The newest message of each conversation, all in one query (uses idx_message_conversation_sent_at). */
	@Query("""
			SELECT m FROM Message m
			WHERE m.conversation.id IN :conversationIds
			AND m.sentAt = (SELECT MAX(m2.sentAt) FROM Message m2 WHERE m2.conversation.id = m.conversation.id)
			""")
	List<Message> findLatestIn(@Param("conversationIds") Collection<UUID> conversationIds);

	/**
	 * [conversationId, count] of messages the owner hasn't read: sent after their lastReadAt, and not
	 * sent by them. Only conversations with at least one unread message come back.
	 */
	@Query("""
			SELECT m.conversation.id, COUNT(m) FROM Message m, ConversationParticipant p
			WHERE p.conversation.id = m.conversation.id
			AND p.ownerType = :ownerType AND p.ownerId = :ownerId
			AND m.conversation.id IN :conversationIds
			AND (p.lastReadAt IS NULL OR m.sentAt > p.lastReadAt)
			AND (m.senderOwnerId IS NULL OR m.senderOwnerId <> :ownerId OR m.senderOwnerType <> :ownerType)
			GROUP BY m.conversation.id
			""")
	List<Object[]> countUnread(@Param("ownerType") OwnerType ownerType, @Param("ownerId") UUID ownerId,
			@Param("conversationIds") Collection<UUID> conversationIds);

	/** Every unread message across all of the owner's conversations - the Chats tab badge. */
	@Query("""
			SELECT COUNT(m) FROM Message m, ConversationParticipant p
			WHERE p.conversation.id = m.conversation.id
			AND p.ownerType = :ownerType AND p.ownerId = :ownerId AND p.schoolId = :schoolId
			AND (p.lastReadAt IS NULL OR m.sentAt > p.lastReadAt)
			AND (m.senderOwnerId IS NULL OR m.senderOwnerId <> :ownerId OR m.senderOwnerType <> :ownerType)
			""")
	long countAllUnread(@Param("schoolId") UUID schoolId, @Param("ownerType") OwnerType ownerType,
			@Param("ownerId") UUID ownerId);

}
