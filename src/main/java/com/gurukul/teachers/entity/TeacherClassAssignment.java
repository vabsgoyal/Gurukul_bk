package com.gurukul.teachers.entity;

import com.gurukul.common.BaseEntity;
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

@Getter
@Setter
@Entity
@Table(name = "teacher_class_assignment", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"school_id", "teacher_id", "class_section_id", "subject_name", "assignment_role"})
})
public class TeacherClassAssignment extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "teacher_id", nullable = false)
	private Teacher teacher;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "class_section_id", nullable = false)
	private ClassSection classSection;

	@Column(name = "subject_name", nullable = false)
	private String subjectName;

	@Enumerated(EnumType.STRING)
	@Column(name = "assignment_role", nullable = false)
	private TeacherAssignmentRole assignmentRole;

}
