package com.gurukul.chat.repository;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.chat.entity.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, UUID> {

	boolean existsByConversation_IdAndOwnerTypeAndOwnerId(UUID conversationId, OwnerType ownerType, UUID ownerId);

	List<ConversationParticipant> findAllByConversation_Id(UUID conversationId);

	List<ConversationParticipant> findAllByConversation_IdIn(List<UUID> conversationIds);

	@Modifying
	@Query("""
			UPDATE ConversationParticipant p SET p.lastReadAt = :readAt, p.updatedAt = :readAt
			WHERE p.conversation.id = :conversationId AND p.ownerType = :ownerType AND p.ownerId = :ownerId
			""")
	int markRead(@Param("conversationId") UUID conversationId, @Param("ownerType") OwnerType ownerType,
			@Param("ownerId") UUID ownerId, @Param("readAt") Instant readAt);

}
