package com.gurukul.events.entity;

/**
 * Distinct from EventStatus (DRAFT/ACTIVE/CLOSED - the finance lifecycle): this is the
 * participation-facing status, derived at read time from cancelled/startAt/endAt vs now, never
 * persisted, so it can't drift.
 */
public enum EventParticipationStatus {
	UPCOMING,
	ONGOING,
	COMPLETED,
	CANCELLED
}
