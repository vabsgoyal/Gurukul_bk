package com.gurukul.attendance.entity;

import com.gurukul.common.BaseEntity;
import com.gurukul.employees.entity.Employee;
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
@Table(name = "staff_attendance_record", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"school_id", "employee_id", "attendance_date"})
})
public class StaffAttendanceRecord extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "employee_id", nullable = false)
	private Employee employee;

	@Column(name = "attendance_date", nullable = false)
	private LocalDate attendanceDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AttendanceStatus status;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "marked_by_employee_id", nullable = false)
	private Employee markedByEmployee;

	@Column(length = 500)
	private String remarks;

}
