package com.gurukul.workflow.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "approval_history")
public class ApprovalHistory extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "approval_request_id", nullable = false)
	private ApprovalRequest approvalRequest;

	@Enumerated(EnumType.STRING)
	@Column(name = "from_status")
	private ApprovalStatus fromStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "to_status", nullable = false)
	private ApprovalStatus toStatus;

	@Column(name = "changed_by")
	private String changedBy;

	@Column(name = "changed_at", nullable = false)
	private Instant changedAt;

}
