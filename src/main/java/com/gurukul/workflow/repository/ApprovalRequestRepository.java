package com.gurukul.workflow.repository;

import com.gurukul.workflow.entity.ApprovalRequest;
import com.gurukul.workflow.entity.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

	Optional<ApprovalRequest> findByEntityTypeAndEntityId(String entityType, UUID entityId);

	Optional<ApprovalRequest> findByIdAndSchoolId(UUID id, UUID schoolId);

	List<ApprovalRequest> findAllBySchoolIdAndEntityTypeAndStatusOrderByCreatedAtAsc(
			UUID schoolId, String entityType, ApprovalStatus status);

}
