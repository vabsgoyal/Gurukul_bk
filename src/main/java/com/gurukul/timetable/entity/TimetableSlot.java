package com.gurukul.timetable.entity;

import com.gurukul.academics.entity.Subject;
import com.gurukul.common.BaseEntity;
import com.gurukul.employees.entity.Employee;
import com.gurukul.students.entity.ClassSection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.DayOfWeek;

@Getter
@Setter
@Entity
@Table(name = "timetable_slot", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"section_id", "day_of_week", "period_number"}),
		@UniqueConstraint(columnNames = {"school_id", "academic_year", "teacher_id", "day_of_week", "period_number"})
})
public class TimetableSlot extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "section_id", nullable = false)
	private ClassSection section;

	/** Copied from the section so clash detection is scoped to one academic year. */
	@Column(name = "academic_year", nullable = false)
	private String academicYear;

	@Enumerated(EnumType.STRING)
	@Column(name = "day_of_week", nullable = false, length = 10)
	private DayOfWeek dayOfWeek;

	@Column(name = "period_number", nullable = false)
	private int periodNumber;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "subject_id", nullable = false)
	private Subject subject;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "teacher_id", nullable = false)
	private Employee teacher;

}
