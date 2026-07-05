package com.gurukul.collections.repository;

import com.gurukul.collections.entity.EventParticipationFee;
import com.gurukul.collections.entity.ParticipantType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventParticipationFeeRepository extends JpaRepository<EventParticipationFee, UUID> {

	List<EventParticipationFee> findAllByEventId(UUID eventId);

	Optional<EventParticipationFee> findByEventIdAndParticipantType(UUID eventId, ParticipantType participantType);

}
