package com.gurukul.attendance.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A registered physical RFID/fingerprint/face-recognition device at a school. The device
 * authenticates with apiKeyHash (BCrypt) via a shared-secret header, never a human JWT - see
 * AttendanceDeviceEventService for the manual key-check pattern this mirrors from OpsController.
 */
@Getter
@Setter
@Entity
@Table(name = "attendance_device")
public class AttendanceDevice extends BaseEntity {

	@Column(nullable = false, length = 200)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "device_type", nullable = false)
	private AttendanceMethod deviceType;

	@Column(name = "api_key_hash", nullable = false, length = 200)
	private String apiKeyHash;

	@Column(nullable = false)
	private boolean active = true;

	@Column(name = "last_seen_at")
	private Instant lastSeenAt;

}
