package com.gurukul.events.entity;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** One row per (event, participant) - resubmitting an RSVP updates this row rather than adding a new one. */
@Getter
@Setter
@Entity
@Table(name = "event_rsvp", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"event_id", "owner_id", "owner_type"})
})
public class EventRsvp extends BaseEntity {

	@Column(name = "event_id", nullable = false)
	private UUID eventId;

	@Column(name = "owner_id", nullable = false)
	private UUID ownerId;

	@Enumerated(EnumType.STRING)
	@Column(name = "owner_type", nullable = false)
	private OwnerType ownerType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EventRsvpStatus status;

}
