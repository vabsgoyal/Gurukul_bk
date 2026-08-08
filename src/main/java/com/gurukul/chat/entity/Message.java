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
 *
 * <p>content is nullable: a message can be attachment-only (an image/PDF with no caption) - see
 * V38 CHECK constraint requiring at least one of content/attachmentObjectKey. attachmentObjectKey
 * is the S3 key, not a URL - AttachmentService.presignDownload freshly signs a GET url on every
 * read, so a message from months ago never shows an expired link.
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

	@Column(columnDefinition = "TEXT")
	private String content;

	@Column(name = "attachment_object_key")
	private String attachmentObjectKey;

	@Column(name = "attachment_content_type")
	private String attachmentContentType;

	@Column(name = "attachment_file_name")
	private String attachmentFileName;

	@Column(name = "sent_at", nullable = false)
	private Instant sentAt;

}
