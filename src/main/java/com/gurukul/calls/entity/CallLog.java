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

/**
 * One row per call session, whether it originated from an immediate call or from starting a
 * {@link ScheduledCall}. For a scheduled call's session, calleeOwnerType/calleeOwnerId are left
 * null - see scheduledCallId and CallInvitee for the actual participant list in that case.
 */
@Getter
@Setter
@Entity
@Table(name = "call_log")
public class CallLog extends BaseEntity {

	@Column(name = "scheduled_call_id")
	private UUID scheduledCallId;

	@Enumerated(EnumType.STRING)
	@Column(name = "caller_owner_type", nullable = false)
	private OwnerType callerOwnerType;

	@Column(name = "caller_owner_id", nullable = false)
	private UUID callerOwnerId;

	@Enumerated(EnumType.STRING)
	@Column(name = "callee_owner_type")
	private OwnerType calleeOwnerType;

	@Column(name = "callee_owner_id")
	private UUID calleeOwnerId;

	@Column(name = "room_name", nullable = false)
	private String roomName;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "ended_at")
	private Instant endedAt;

	@Column(name = "duration_seconds")
	private Long durationSeconds;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CallOutcome outcome;

}
