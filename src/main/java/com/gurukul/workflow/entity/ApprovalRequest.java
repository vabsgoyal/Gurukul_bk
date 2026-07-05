package com.gurukul.workflow.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "approval_request", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"entity_type", "entity_id"})
})
public class ApprovalRequest extends BaseEntity {

	@Column(name = "entity_type", nullable = false)
	private String entityType;

	@Column(name = "entity_id", nullable = false)
	private UUID entityId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ApprovalStatus status;

	@Column(name = "submitted_by")
	private String submittedBy;

	@Column(name = "approved_by")
	private String approvedBy;

	@Column(length = 500)
	private String comment;

}
