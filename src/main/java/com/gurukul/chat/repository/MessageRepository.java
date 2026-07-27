package com.gurukul.chat.repository;

import com.gurukul.chat.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

	/**
	 * Reused for both REST paginated history (large page sizes) and the bot's bounded
	 * conversation-history window (small page sizes) - same query, different Pageable.
	 */
	Page<Message> findAllByConversation_IdOrderBySentAtDesc(UUID conversationId, Pageable pageable);

}
