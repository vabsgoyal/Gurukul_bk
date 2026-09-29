package com.gurukul.admissions.entity;

import com.gurukul.common.BaseEntity;
import com.gurukul.students.entity.Gender;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One admission application, entered by an admin. The class applied for is a class name (e.g.
 * "Grade 5") chosen up front; the section is only assigned at enrolment, since it depends on seats.
 * Section and student are plain UUID columns rather than JPA relations, so building a response never
 * needs a lazy load outside a transaction.
 */
@Getter
@Setter
@Entity
@Table(name = "admission_application")
public class AdmissionApplication extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AdmissionStage stage;

	@Column(name = "student_name", nullable = false)
	private String studentName;

	@Column(nullable = false)
	private LocalDate dob;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Gender gender;

	@Column(nullable = false)
	private String address;

	@Column(name = "previous_school_name")
	private String previousSchoolName;

	@Column(name = "parent_name", nullable = false)
	private String parentName;

	@Column(name = "parent_contact", nullable = false)
	private String parentContact;

	@Column(name = "parent_email")
	private String parentEmail;

	@Column(name = "applied_class_name", nullable = false)
	private String appliedClassName;

	@Column(name = "assigned_class_section_id")
	private UUID assignedClassSectionId;

	private String notes;

	@Column(name = "student_id")
	private UUID studentId;

	@Column(name = "decided_at")
	private Instant decidedAt;

	@Column(name = "enrolled_at")
	private Instant enrolledAt;

}
