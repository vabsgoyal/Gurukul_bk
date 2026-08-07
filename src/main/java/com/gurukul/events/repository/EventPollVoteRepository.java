package com.gurukul.events.repository;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.events.entity.EventPollVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventPollVoteRepository extends JpaRepository<EventPollVote, UUID> {

	Optional<EventPollVote> findByEventIdAndOwnerIdAndOwnerType(UUID eventId, UUID ownerId, OwnerType ownerType);

	List<EventPollVote> findAllByEventId(UUID eventId);

}
