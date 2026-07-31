package com.gurukul.gamification.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Teacher-awarded "spot recognition" only - automatic XP-based house contribution is never
 * stored here, it's computed live by summing XpEvent for a house's members (see HouseService),
 * matching the same live-aggregation approach Phase 2 used for weekly league XP.
 */
@Getter
@Setter
@Entity
@Table(name = "house_point_event")
public class HousePointEvent extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "house_id", nullable = false)
	private House house;

	@Column(name = "student_id", nullable = false)
	private UUID studentId;

	@Column(nullable = false)
	private int amount;

	@Column(nullable = false, length = 300)
	private String reason;

	@Column(name = "awarded_by_employee_id", nullable = false)
	private UUID awardedByEmployeeId;

}
