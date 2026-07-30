package com.gurukul.calls.service;

import com.gurukul.auth.entity.OwnerType;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory "who's currently ringing or on a call" tracker, used only to reject/queue a second
 * immediate call (4.3: busy -> auto-reject). Deliberately not persisted - this is a single-instance
 * deployment (one EC2 box, one JVM), so there is no multi-instance consistency concern that would
 * justify a shared store like Redis. Cleared whenever a call resolves (accepted-and-ended,
 * declined, missed, busy, or cancelled).
 */
@Component
public class ActiveCallRegistry {

	private final ConcurrentHashMap<String, UUID> activeByOwner = new ConcurrentHashMap<>();

	public boolean isBusy(OwnerType ownerType, UUID ownerId) {
		return activeByOwner.containsKey(key(ownerType, ownerId));
	}

	public void markActive(OwnerType ownerType, UUID ownerId, UUID callLogId) {
		activeByOwner.put(key(ownerType, ownerId), callLogId);
	}

	public void clear(OwnerType ownerType, UUID ownerId) {
		activeByOwner.remove(key(ownerType, ownerId));
	}

	private String key(OwnerType ownerType, UUID ownerId) {
		return ownerType + ":" + ownerId;
	}

}
