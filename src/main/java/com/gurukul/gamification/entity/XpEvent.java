package com.gurukul.gamification.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Append-only in spirit - the one exception is recordAttendanceXp's idempotent update when a
 * teacher edits an already-marked day's attendance, which adjusts the existing ATTENDANCE event
 * for that (student, relatedDate) rather than creating a duplicate. relatedDate is the calendar
 * date this event is *about* (e.g. the attendance date), separate from BaseEntity.createdAt
 * (when the event was recorded) - null for sources that aren't tied to a specific date.
 */
@Getter
@Setter
@Entity
@Table(name = "xp_event")
public class XpEvent extends BaseEntity {

	@Column(name = "student_id", nullable = false)
	private UUID studentId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private XpSource source;

	@Column(nullable = false)
	private int amount;

	@Column(name = "related_date")
	private LocalDate relatedDate;

}
