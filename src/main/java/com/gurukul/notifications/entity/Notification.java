package com.gurukul.notifications.entity;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One inbox row per recipient per push (see PushNotificationService.sendEach). {@code data} is the
 * push's JSON payload, kept as text so the app can route a tap on an inbox row exactly as it routes
 * a tap on the push itself.
 */
@Getter
@Setter
@Entity
@Table(name = "notification")
public class Notification extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(name = "recipient_owner_type", nullable = false)
	private OwnerType recipientOwnerType;

	@Column(name = "recipient_owner_id", nullable = false)
	private UUID recipientOwnerId;

	@Column(nullable = false)
	private String type;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String body;

	@Column(columnDefinition = "TEXT")
	private String data;

	@Column(name = "dedupe_key")
	private String dedupeKey;

	@Column(name = "read_at")
	private Instant readAt;

}
