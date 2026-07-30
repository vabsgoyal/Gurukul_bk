package com.gurukul.calls.entity;

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

@Getter
@Setter
@Entity
@Table(name = "scheduled_call")
public class ScheduledCall extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(name = "host_owner_type", nullable = false)
	private OwnerType hostOwnerType;

	@Column(name = "host_owner_id", nullable = false)
	private UUID hostOwnerId;

	@Column(nullable = false)
	private String title;

	@Column(name = "scheduled_at", nullable = false)
	private Instant scheduledAt;

	@Column(name = "room_name", nullable = false, unique = true)
	private String roomName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CallStatus status;

	@Column(name = "reminder_sent", nullable = false)
	private boolean reminderSent;

}
