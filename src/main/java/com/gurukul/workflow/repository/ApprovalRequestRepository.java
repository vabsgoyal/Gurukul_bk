package com.gurukul.workflow.repository;

import com.gurukul.workflow.entity.ApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

	Optional<ApprovalRequest> findByEntityTypeAndEntityId(String entityType, UUID entityId);

	Optional<ApprovalRequest> findByIdAndSchoolId(UUID id, UUID schoolId);

}
