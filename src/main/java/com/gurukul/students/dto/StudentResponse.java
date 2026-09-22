package com.gurukul.students.dto;

import com.gurukul.common.crypto.TokenCipher;
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

	@Schema(description = "Roll number within the class-section - server-computed from alphabetical order, not admin-entered", example = "1")
	private String rollNumber;

	@Schema(description = "System-generated at admission, unique per school - the identifier the student/parent use to self-register a login", example = "2026000001")
	private String registrationNumber;

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

	@Schema(description = "This class-section's assigned class teacher, if any")
	private UUID classTeacherId;

	@Schema(description = "Class teacher's name, if assigned")
	private String classTeacherName;

	@Schema(description = "Admission date", example = "2026-04-01")
	private LocalDate admissionDate;

	@Schema(description = "Current enrollment status", example = "ACTIVE")
	private StudentStatus status;

	@Schema(description = "When the record was created")
	private Instant createdAt;

	@Schema(description = "When the record was last updated")
	private Instant updatedAt;

	@Schema(description = "State student-tracking ID (e.g. MP SSSM/Samagra ID). Admin-only, null otherwise.")
	private String sssmId;

	@Schema(description = "Aadhaar number, decrypted. Admin-only, null otherwise.")
	private String aadhaarNumber;

	@Schema(description = "Caste, as recorded for RTE quota tracking. Admin-only, null otherwise.")
	private String caste;

	@Schema(description = "Category, as recorded for RTE quota tracking. Admin-only, null otherwise.")
	private String category;

	@Schema(description = "Annual family income in rupees. Admin-only, null otherwise.")
	private Long annualIncome;

	@Schema(description = "Previous school name, if transferred in. Admin-only, null otherwise.")
	private String previousSchoolName;

	@Schema(description = "Bank account number, decrypted, used for RTE fee reimbursement. Admin-only, null otherwise.")
	private String bankAccountNumber;

	@Schema(description = "IFSC code for the bank account above. Admin-only, null otherwise.")
	private String bankIfsc;

	/**
	 * registrationNumber is a login-claim key (student self-registration auto-activates on it alone),
	 * and the RTE/regulatory block (sssmId through bankIfsc) includes Aadhaar + bank account number -
	 * both only an admin should ever see, and both stored encrypted so {@code cipher} decrypts them
	 * here, on the way out, rather than anywhere upstream. Everyone else gets null for all of it, same
	 * as any other field they're not entitled to. {@code cipher} may be null when isAdmin is false.
	 */
	public static StudentResponse from(Student student, boolean isAdmin, TokenCipher cipher) {
		return new StudentResponse(
				student.getId(),
				student.getSchoolId(),
				student.getRollNumber(),
				isAdmin ? student.getRegistrationNumber() : null,
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
				student.getClassSection().getClassTeacher() != null ? student.getClassSection().getClassTeacher().getId() : null,
				student.getClassSection().getClassTeacher() != null ? student.getClassSection().getClassTeacher().getName() : null,
				student.getAdmissionDate(),
				student.getStatus(),
				student.getCreatedAt(),
				student.getUpdatedAt(),
				isAdmin ? student.getSssmId() : null,
				isAdmin && student.getAadhaarNumberEncrypted() != null ? cipher.decrypt(student.getAadhaarNumberEncrypted()) : null,
				isAdmin ? student.getCaste() : null,
				isAdmin ? student.getCategory() : null,
				isAdmin ? student.getAnnualIncome() : null,
				isAdmin ? student.getPreviousSchoolName() : null,
				isAdmin && student.getBankAccountNumberEncrypted() != null ? cipher.decrypt(student.getBankAccountNumberEncrypted()) : null,
				isAdmin ? student.getBankIfsc() : null
		);
	}

}
