package com.gurukul.students.dto;

import com.gurukul.students.entity.Gender;
import com.gurukul.students.entity.StudentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Schema(description = "One-time student enrollment payload with essential fields")
public class StudentRequest {

	@NotBlank
	@Schema(description = "Unique roll number within the school", example = "8A-001")
	private String rollNumber;

	@NotBlank
	@Schema(description = "Full name of the student", example = "Rahul Sharma")
	private String name;

	@NotNull
	@Past
	@Schema(description = "Date of birth (must be in the past)", example = "2012-05-15")
	private LocalDate dob;

	@NotNull
	@Schema(description = "Student gender", example = "MALE")
	private Gender gender;

	@NotBlank
	@Schema(description = "Home address", example = "123 MG Road, Jaipur")
	private String address;

	@NotBlank
	@Schema(description = "Parent or guardian full name", example = "Rajesh Sharma")
	private String parentName;

	@NotBlank
	@Schema(description = "Parent or guardian contact phone number", example = "9876543210")
	private String parentContact;

	@NotNull
	@Schema(description = "Class-section UUID from GET /api/v1/class-sections")
	private UUID classSectionId;

	@NotNull
	@PastOrPresent
	@Schema(description = "Date the student was admitted", example = "2026-04-01")
	private LocalDate admissionDate;

	@Schema(
			description = "Student lifecycle status. Defaults to ACTIVE on create; optional on update.",
			example = "ACTIVE",
			allowableValues = {"ACTIVE", "ALUMNI", "WITHDRAWN"}
	)
	private StudentStatus status;

}
