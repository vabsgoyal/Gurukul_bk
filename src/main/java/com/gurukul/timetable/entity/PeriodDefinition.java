package com.gurukul.timetable.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/** One period in a school's single bell schedule (the same times apply to every section and day). */
@Getter
@Setter
@Entity
@Table(name = "period_definition", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"school_id", "period_number"})
})
public class PeriodDefinition extends BaseEntity {

	@Column(name = "period_number", nullable = false)
	private int periodNumber;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	/** A break (recess/lunch) is shown on the timetable but can never hold a subject. */
	@Column(name = "is_break", nullable = false)
	private boolean breakPeriod;

	@Column(length = 50)
	private String label;

}
