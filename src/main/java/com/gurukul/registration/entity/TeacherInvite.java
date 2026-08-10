package com.gurukul.registration.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Admin-issued, single-use, expiring code a prospective teacher needs to self-register. */
@Getter
@Setter
@Entity
@Table(name = "teacher_invite")
public class TeacherInvite extends BaseEntity {

	@Column(nullable = false, unique = true)
	private String code;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(nullable = false)
	private boolean used;

	@Column(name = "created_by_employee_id", nullable = false)
	private UUID createdByEmployeeId;

	/** The specific Employee this invite claims a credential for - null only for invites issued before this column existed. */
	@Column(name = "target_employee_id")
	private UUID targetEmployeeId;

}
