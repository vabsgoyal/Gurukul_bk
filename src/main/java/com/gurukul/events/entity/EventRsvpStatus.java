package com.gurukul.events.entity;

/**
 * No PENDING state, unlike calls.entity.RsvpStatus: nobody is pre-invited to an event, so there's
 * simply no row until a participant responds.
 */
public enum EventRsvpStatus {
	ACCEPTED,
	DECLINED,
	MAYBE
}
