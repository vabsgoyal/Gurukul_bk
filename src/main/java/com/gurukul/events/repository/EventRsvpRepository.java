package com.gurukul.events.repository;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.events.entity.EventRsvp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRsvpRepository extends JpaRepository<EventRsvp, UUID> {

	Optional<EventRsvp> findByEventIdAndOwnerIdAndOwnerType(UUID eventId, UUID ownerId, OwnerType ownerType);

	List<EventRsvp> findAllByEventId(UUID eventId);

}
