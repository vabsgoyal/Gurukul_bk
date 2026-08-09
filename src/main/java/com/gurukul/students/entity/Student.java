package com.gurukul.students.entity;

import com.gurukul.common.BaseEntity;
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
@Table(name = "student", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"school_id", "roll_number"}),
		@UniqueConstraint(columnNames = {"school_id", "registration_number"})
})
public class Student extends BaseEntity {

	@Column(nullable = false)
	private String rollNumber;

	/**
	 * System-generated at admission time (see DocumentNumberGenerator.nextRegistrationNumber) -
	 * never admin-typed, unlike rollNumber. Deliberately distinct from rollNumber: this is the
	 * permanent claim key a student uses to self-register a login (see RegistrationService), so it
	 * needs to stay stable even if rollNumber ever changes (e.g. on class/section reassignment).
	 */
	@Column(name = "registration_number", nullable = false)
	private String registrationNumber;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private LocalDate dob;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Gender gender;

	@Column(nullable = false)
	private String address;

	@Column(nullable = false)
	private String parentName;

	@Column(nullable = false)
	private String parentContact;

	@Column(nullable = false)
	private LocalDate admissionDate;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "class_section_id", nullable = false)
	private ClassSection classSection;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private StudentStatus status;

}
