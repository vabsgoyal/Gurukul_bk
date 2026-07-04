package com.gurukul.students.dto;

import com.gurukul.students.entity.Student;
import com.gurukul.students.entity.StudentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "Enrolled student record returned by the API")
public class StudentResponse {

	@Schema(description = "Unique student identifier")
	private UUID id;

	@Schema(description = "School (tenant) this student belongs to")
	private UUID schoolId;

	@Schema(description = "Roll number unique within the school", example = "8A-001")
	private String rollNumber;

	@Schema(description = "Full name", example = "Rahul Sharma")
	private String name;

	@Schema(description = "Date of birth", example = "2012-05-15")
	private LocalDate dob;

	@Schema(description = "Student gender", example = "MALE")
	private String gender;

	@Schema(description = "Home address")
	private String address;

	@Schema(description = "Parent or guardian name")
	private String parentName;

	@Schema(description = "Parent or guardian contact", example = "9876543210")
	private String parentContact;

	@Schema(description = "Class-section UUID")
	private UUID classSectionId;

	@Schema(description = "Class or grade name", example = "Grade 8")
	private String className;

	@Schema(description = "Section", example = "A")
	private String section;

	@Schema(description = "Academic year", example = "2026-27")
	private String academicYear;

	@Schema(description = "Display label for class-section", example = "Grade 8 - A (2026-27)")
	private String classSectionLabel;

	@Schema(description = "Admission date", example = "2026-04-01")
	private LocalDate admissionDate;

	@Schema(description = "Current enrollment status", example = "ACTIVE")
	private StudentStatus status;

	@Schema(description = "When the record was created")
	private Instant createdAt;

	@Schema(description = "When the record was last updated")
	private Instant updatedAt;

	public static StudentResponse from(Student student) {
		return new StudentResponse(
				student.getId(),
				student.getSchoolId(),
				student.getRollNumber(),
				student.getName(),
				student.getDob(),
				student.getGender().name(),
				student.getAddress(),
				student.getParentName(),
				student.getParentContact(),
				student.getClassSection().getId(),
				student.getClassSection().getClassName(),
				student.getClassSection().getSection(),
				student.getClassSection().getAcademicYear(),
				student.getClassSection().getDisplayLabel(),
				student.getAdmissionDate(),
				student.getStatus(),
				student.getCreatedAt(),
				student.getUpdatedAt()
		);
	}

}
