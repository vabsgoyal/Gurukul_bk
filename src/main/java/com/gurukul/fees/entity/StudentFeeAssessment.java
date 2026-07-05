package com.gurukul.fees.entity;

import com.gurukul.common.BaseEntity;
import com.gurukul.students.entity.Student;
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

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "student_fee_assessment", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"school_id", "student_id", "academic_year"})
})
public class StudentFeeAssessment extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "student_id", nullable = false)
	private Student student;

	@Column(name = "academic_year", nullable = false)
	private String academicYear;

	@Column(name = "total_due", nullable = false, precision = 12, scale = 2)
	private BigDecimal totalDue;

	@Column(name = "total_paid", nullable = false, precision = 12, scale = 2)
	private BigDecimal totalPaid = BigDecimal.ZERO;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FeeAssessmentStatus status;

	@Column(name = "due_date")
	private LocalDate dueDate;

}
