package com.gurukul.calls.repository;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.calls.entity.CallStatus;
import com.gurukul.calls.entity.ScheduledCall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduledCallRepository extends JpaRepository<ScheduledCall, UUID> {

	Optional<ScheduledCall> findByIdAndSchoolId(UUID id, UUID schoolId);

	List<ScheduledCall> findAllBySchoolIdAndHostOwnerTypeAndHostOwnerIdOrderByScheduledAtDesc(
			UUID schoolId, OwnerType hostOwnerType, UUID hostOwnerId);

	List<ScheduledCall> findAllByStatusAndReminderSentFalseAndScheduledAtLessThanEqual(
			CallStatus status, Instant cutoff);

	List<ScheduledCall> findAllByStatusAndScheduledAtBefore(CallStatus status, Instant cutoff);

}
