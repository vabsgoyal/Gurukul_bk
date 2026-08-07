package com.gurukul.events.repository;

import com.gurukul.events.entity.EventPollOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventPollOptionRepository extends JpaRepository<EventPollOption, UUID> {

	List<EventPollOption> findAllByEventId(UUID eventId);

	Optional<EventPollOption> findByIdAndEventId(UUID id, UUID eventId);

}
