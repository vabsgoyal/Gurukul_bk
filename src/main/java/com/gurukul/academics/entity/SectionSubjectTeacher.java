package com.gurukul.academics.entity;

import com.gurukul.common.BaseEntity;
import com.gurukul.employees.entity.Employee;
import com.gurukul.students.entity.ClassSection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "section_subject_teacher", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"section_id", "subject_id", "teacher_id"})
})
public class SectionSubjectTeacher extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "section_id", nullable = false)
	private ClassSection section;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "subject_id", nullable = false)
	private Subject subject;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "teacher_id", nullable = false)
	private Employee teacher;

}
