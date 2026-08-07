package com.gurukul.events.repository;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.events.entity.EventRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRegistrationRepository extends JpaRepository<EventRegistration, UUID> {

	Optional<EventRegistration> findByEventIdAndOwnerIdAndOwnerType(UUID eventId, UUID ownerId, OwnerType ownerType);

	List<EventRegistration> findAllByEventId(UUID eventId);

}
