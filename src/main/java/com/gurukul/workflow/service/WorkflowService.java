package com.gurukul.workflow.service;

import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.workflow.entity.ApprovalHistory;
import com.gurukul.workflow.entity.ApprovalRequest;
import com.gurukul.workflow.entity.ApprovalStatus;
import com.gurukul.workflow.repository.ApprovalHistoryRepository;
import com.gurukul.workflow.repository.ApprovalRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowService {

	private final ApprovalRequestRepository approvalRequestRepository;
	private final ApprovalHistoryRepository approvalHistoryRepository;
	private final SchoolContext schoolContext;

	@Transactional
	public ApprovalRequest getOrCreate(String entityType, UUID entityId) {
		return approvalRequestRepository.findByEntityTypeAndEntityId(entityType, entityId)
				.orElseGet(() -> {
					ApprovalRequest request = new ApprovalRequest();
					request.setSchoolId(schoolContext.getSchoolId());
					request.setEntityType(entityType);
					request.setEntityId(entityId);
					request.setStatus(ApprovalStatus.DRAFT);
					return approvalRequestRepository.save(request);
				});
	}

	@Transactional
	public ApprovalRequest submit(String entityType, UUID entityId, String submittedBy) {
		ApprovalRequest request = getOrCreate(entityType, entityId);
		if (request.getStatus() != ApprovalStatus.DRAFT && request.getStatus() != ApprovalStatus.REJECTED) {
			throw new IllegalArgumentException("Cannot submit request in status: " + request.getStatus());
		}
		recordHistory(request, request.getStatus(), ApprovalStatus.SUBMITTED, submittedBy);
		request.setStatus(ApprovalStatus.SUBMITTED);
		request.setSubmittedBy(submittedBy);
		return approvalRequestRepository.save(request);
	}

	@Transactional
	public ApprovalRequest approve(String entityType, UUID entityId, String approvedBy, String comment) {
		ApprovalRequest request = findByEntity(entityType, entityId);
		if (request.getStatus() != ApprovalStatus.SUBMITTED) {
			throw new IllegalArgumentException("Only submitted requests can be approved");
		}
		recordHistory(request, request.getStatus(), ApprovalStatus.APPROVED, approvedBy);
		request.setStatus(ApprovalStatus.APPROVED);
		request.setApprovedBy(approvedBy);
		request.setComment(comment);
		return approvalRequestRepository.save(request);
	}

	@Transactional
	public ApprovalRequest reject(String entityType, UUID entityId, String rejectedBy, String comment) {
		ApprovalRequest request = findByEntity(entityType, entityId);
		if (request.getStatus() != ApprovalStatus.SUBMITTED) {
			throw new IllegalArgumentException("Only submitted requests can be rejected");
		}
		recordHistory(request, request.getStatus(), ApprovalStatus.REJECTED, rejectedBy);
		request.setStatus(ApprovalStatus.REJECTED);
		request.setApprovedBy(rejectedBy);
		request.setComment(comment);
		return approvalRequestRepository.save(request);
	}

	public ApprovalRequest findByEntity(String entityType, UUID entityId) {
		return approvalRequestRepository.findByEntityTypeAndEntityId(entityType, entityId)
				.orElseThrow(() -> new EntityNotFoundException("Approval request not found"));
	}

	private void recordHistory(ApprovalRequest request, ApprovalStatus from, ApprovalStatus to, String changedBy) {
		ApprovalHistory history = new ApprovalHistory();
		history.setSchoolId(request.getSchoolId());
		history.setApprovalRequest(request);
		history.setFromStatus(from);
		history.setToStatus(to);
		history.setChangedBy(changedBy);
		history.setChangedAt(Instant.now());
		approvalHistoryRepository.save(history);
	}

}
