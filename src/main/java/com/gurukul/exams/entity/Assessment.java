package com.gurukul.exams.entity;

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
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "assessment")
public class Assessment extends BaseEntity {

	@Column(nullable = false)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AssessmentType type;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "section_id", nullable = false)
	private ClassSection section;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "subject_id")
	private Subject subject;

	@Column(name = "assessment_date", nullable = false)
	private LocalDate assessmentDate;

	@Column(name = "max_marks", nullable = false, precision = 5, scale = 2)
	private BigDecimal maxMarks;

	@Column(length = 1000)
	private String description;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by_teacher_id")
	private Employee createdByTeacher;

	/** Free-text grouping for report-card purposes (e.g. "Term 1") - nullable, set by whoever
	 * creates the assessment. Not a fixed enum: schools structure their terms differently. */
	@Column(length = 50)
	private String term;

}
