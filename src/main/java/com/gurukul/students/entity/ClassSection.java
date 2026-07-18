package com.gurukul.students.entity;

import com.gurukul.common.BaseEntity;
import com.gurukul.employees.entity.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "class_section", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"school_id", "class_name", "section", "academic_year"})
})
public class ClassSection extends BaseEntity {

	@Column(name = "class_name", nullable = false)
	private String className;

	@Column(nullable = false)
	private String section;

	@Column(name = "academic_year", nullable = false)
	private String academicYear;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "class_teacher_id")
	private Employee classTeacher;

	public String getDisplayLabel() {
		return className + " - " + section + " (" + academicYear + ")";
	}

}
