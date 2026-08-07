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

/**
 * One registration per (event, submitter). "Team Members" style fields are free text answered by
 * a single submitter - team-mates named in the text aren't linked accounts, this is a roster
 * entry, not a multi-user registration.
 */
@Getter
@Setter
@Entity
@Table(name = "event_registration", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"event_id", "owner_id", "owner_type"})
})
public class EventRegistration extends BaseEntity {

	@Column(name = "event_id", nullable = false)
	private UUID eventId;

	@Column(name = "owner_id", nullable = false)
	private UUID ownerId;

	@Enumerated(EnumType.STRING)
	@Column(name = "owner_type", nullable = false)
	private OwnerType ownerType;

	/** JSON object of {fieldKey: answer}, keyed against the parent SchoolEvent's registrationFieldsJson. */
	@Column(nullable = false, columnDefinition = "TEXT")
	private String answers;

}
