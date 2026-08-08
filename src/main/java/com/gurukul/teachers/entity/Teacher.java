package com.gurukul.teachers.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "teacher", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"school_id", "employee_code"}),
		@UniqueConstraint(columnNames = {"school_id", "email"})
})
public class Teacher extends BaseEntity {

	@Column(name = "employee_code", nullable = false)
	private String employeeCode;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String email;

	@Column(nullable = false)
	private String phone;

	@Column(nullable = false)
	private String qualification;

	@Column(nullable = false)
	private String specialization;

	@Column(name = "joining_date", nullable = false)
	private LocalDate joiningDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TeacherStatus status;

}
