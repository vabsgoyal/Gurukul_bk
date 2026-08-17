package com.gurukul.chat.repository;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.chat.entity.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, UUID> {

	boolean existsByConversation_IdAndOwnerTypeAndOwnerId(UUID conversationId, OwnerType ownerType, UUID ownerId);

	List<ConversationParticipant> findAllByConversation_Id(UUID conversationId);

	List<ConversationParticipant> findAllByConversation_IdIn(List<UUID> conversationIds);

}
