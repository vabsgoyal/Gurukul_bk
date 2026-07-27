package com.gurukul.chat.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * No participant columns here - see {@link ConversationParticipant}. A BOT conversation has
 * exactly one participant row (the human); there is no row for "the bot side".
 */
@Getter
@Setter
@Entity
@Table(name = "conversation")
public class Conversation extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ConversationType type;

}
