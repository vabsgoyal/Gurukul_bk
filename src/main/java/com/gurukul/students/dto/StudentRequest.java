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

	// --- Optional RTE/regulatory fields - omit any/all if the school's register doesn't have them.
	// aadhaarNumber and bankAccountNumber are encrypted before storage (see StudentService); they are
	// plaintext only on this inbound request, never persisted or echoed back as plaintext.

	@Schema(description = "State student-tracking ID (e.g. Madhya Pradesh SSSM/Samagra ID), if the school's register has one", example = "310755266")
	private String sssmId;

	@Schema(description = "Aadhaar number - encrypted before storage, admin-only on read", example = "349877211234")
	private String aadhaarNumber;

	@Schema(description = "Caste, as recorded for RTE quota tracking", example = "Patidar")
	private String caste;

	@Schema(description = "Category, as recorded for RTE quota tracking", example = "OBC")
	private String category;

	@Schema(description = "Annual family income in rupees, as recorded for RTE quota tracking", example = "100000")
	private Long annualIncome;

	@Schema(description = "Previous school name, if the student transferred in", example = "Noble Academy, Unhel")
	private String previousSchoolName;

	@Schema(description = "Bank account number used for RTE fee reimbursement - encrypted before storage, admin-only on read")
	private String bankAccountNumber;

	@Schema(description = "IFSC code for the bank account above", example = "BARB0UNHELX")
	private String bankIfsc;

}
