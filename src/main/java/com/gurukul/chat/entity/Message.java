package com.gurukul.chat.entity;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * senderOwnerType/senderOwnerId are null exactly when senderKind = BOT (see V18 CHECK constraint).
 */
@Getter
@Setter
@Entity
@Table(name = "message")
public class Message extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "conversation_id", nullable = false)
	private Conversation conversation;

	@Enumerated(EnumType.STRING)
	@Column(name = "sender_kind", nullable = false)
	private SenderKind senderKind;

	@Enumerated(EnumType.STRING)
	@Column(name = "sender_owner_type")
	private OwnerType senderOwnerType;

	@Column(name = "sender_owner_id")
	private java.util.UUID senderOwnerId;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(name = "sent_at", nullable = false)
	private Instant sentAt;

}
