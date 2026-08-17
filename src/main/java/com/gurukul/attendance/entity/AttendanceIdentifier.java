package com.gurukul.attendance.entity;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Maps a device-reported external id (RFID card UID, fingerprint template id, or a
 * face-recognition vendor's subject id) to the student/employee it belongs to. Uniqueness among
 * active rows is enforced in AttendanceIdentifierService, not a DB constraint - see the V49
 * migration comment for why.
 */
@Getter
@Setter
@Entity
@Table(name = "attendance_identifier")
public class AttendanceIdentifier extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(name = "owner_type", nullable = false)
	private OwnerType ownerType;

	@Column(name = "owner_id", nullable = false)
	private UUID ownerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AttendanceMethod method;

	@Column(name = "external_id", nullable = false, length = 200)
	private String externalId;

	@Column(nullable = false)
	private boolean active = true;

}
