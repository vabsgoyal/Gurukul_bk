package com.gurukul.registration.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Tracks failed parent-registration claim attempts per (school, student registrationNumber) to rate-limit guessing parentContact. */
@Getter
@Setter
@Entity
@Table(name = "parent_claim_attempt")
public class ParentClaimAttempt extends BaseEntity {

	@Column(name = "student_registration_number", nullable = false)
	private String studentRegistrationNumber;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "locked_until")
	private Instant lockedUntil;

}
