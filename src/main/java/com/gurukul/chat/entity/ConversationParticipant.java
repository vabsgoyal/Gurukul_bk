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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "conversation_participant", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"conversation_id", "owner_type", "owner_id"})
})
public class ConversationParticipant extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "conversation_id", nullable = false)
	private Conversation conversation;

	@Enumerated(EnumType.STRING)
	@Column(name = "owner_type", nullable = false)
	private OwnerType ownerType;

	@Column(name = "owner_id", nullable = false)
	private UUID ownerId;

}
