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

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "marked_by_employee_id")
	private Employee markedByEmployee;

	@Column(length = 500)
	private String remarks;

	@Column(name = "self_marked", nullable = false)
	private boolean selfMarked;

	@Column(name = "marked_latitude")
	private Double markedLatitude;

	@Column(name = "marked_longitude")
	private Double markedLongitude;

	@Column(name = "marked_accuracy_meters")
	private Double markedAccuracyMeters;

	/** Set when a registered device (see AttendanceDevice) marked this record, instead of a human. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "marked_by_device_id")
	private AttendanceDevice markedByDevice;

	@Enumerated(EnumType.STRING)
	@Column
	private AttendanceMethod method;

}
