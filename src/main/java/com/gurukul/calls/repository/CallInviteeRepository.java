package com.gurukul.calls.repository;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.calls.entity.CallInvitee;
import com.gurukul.calls.entity.RsvpStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CallInviteeRepository extends JpaRepository<CallInvitee, UUID> {

	List<CallInvitee> findAllByScheduledCall_Id(UUID scheduledCallId);

	List<CallInvitee> findAllByScheduledCall_IdAndRsvpStatus(UUID scheduledCallId, RsvpStatus rsvpStatus);

	Optional<CallInvitee> findByScheduledCall_IdAndOwnerTypeAndOwnerId(
			UUID scheduledCallId, OwnerType ownerType, UUID ownerId);

	boolean existsByScheduledCall_IdAndOwnerTypeAndOwnerId(UUID scheduledCallId, OwnerType ownerType, UUID ownerId);

	@EntityGraph(attributePaths = "scheduledCall")
	List<CallInvitee> findAllBySchoolIdAndOwnerTypeAndOwnerIdOrderByCreatedAtDesc(
			UUID schoolId, OwnerType ownerType, UUID ownerId);

}
