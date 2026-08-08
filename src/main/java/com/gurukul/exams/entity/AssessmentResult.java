package com.gurukul.exams.entity;

import com.gurukul.common.BaseEntity;
import com.gurukul.students.entity.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "assessment_result", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"school_id", "assessment_id", "student_id"})
})
public class AssessmentResult extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "assessment_id", nullable = false)
	private Assessment assessment;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "student_id", nullable = false)
	private Student student;

	@Column(name = "marks_obtained", nullable = false, precision = 5, scale = 2)
	private BigDecimal marksObtained;

	@Column(length = 500)
	private String remarks;

}
