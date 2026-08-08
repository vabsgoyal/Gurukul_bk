package com.gurukul.teachers.entity;

import com.gurukul.common.BaseEntity;
import com.gurukul.students.entity.ClassSection;
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

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "student_attendance", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"school_id", "class_section_id", "student_id", "attendance_date", "session_name"})
})
public class TeacherStudentAttendance extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "teacher_id", nullable = false)
	private Teacher teacher;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "class_section_id", nullable = false)
	private ClassSection classSection;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "student_id", nullable = false)
	private Student student;

	@Column(name = "attendance_date", nullable = false)
	private LocalDate attendanceDate;

	@Column(name = "session_name", nullable = false)
	private String sessionName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private StudentAttendanceStatus status;

	@Column(length = 500)
	private String remarks;

}
