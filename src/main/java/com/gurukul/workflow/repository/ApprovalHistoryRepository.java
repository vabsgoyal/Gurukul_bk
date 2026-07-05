package com.gurukul.workflow.repository;

import com.gurukul.workflow.entity.ApprovalHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ApprovalHistoryRepository extends JpaRepository<ApprovalHistory, UUID> {
}
