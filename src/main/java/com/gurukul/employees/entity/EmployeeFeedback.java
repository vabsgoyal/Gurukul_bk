package com.gurukul.employees.entity;

import com.gurukul.common.BaseEntity;
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
@Table(name = "employee_feedback")
public class EmployeeFeedback extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "employee_id", nullable = false)
	private Employee employee;

	@Column(nullable = false, precision = 3, scale = 2)
	private BigDecimal rating;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FeedbackCategory category;

	@Column(length = 1000)
	private String comment;

	@Column(name = "feedback_date", nullable = false)
	private LocalDate feedbackDate;

	@Column(name = "submitted_by")
	private String submittedBy;

}
