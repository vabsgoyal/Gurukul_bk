package com.gurukul.calls.service;

import com.gurukul.academics.repository.SectionSubjectTeacherRepository;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.repository.CredentialRepository;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.students.entity.Student;
import com.gurukul.students.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The one place that decides who may call whom. Reused for both the immediate-call and
 * scheduled-call REST paths so the rule can never drift between the two.
 *
 * <p>Allowed, same school only, never with yourself:
 * <ul>
 *   <li>EMPLOYEE &lt;-&gt; EMPLOYEE, where at least one side holds {@link Role#ADMIN} (any staff
 *       member can reach "the principal", and the principal can reach any staff member).</li>
 *   <li>STUDENT &lt;-&gt; EMPLOYEE, where the EMPLOYEE is that student's {@code
 *       ClassSection.classTeacher}, a subject teacher assigned to that section ({@code
 *       SectionSubjectTeacher}), or an admin (full diagnostic reach, matching admin's access
 *       everywhere else - Arena, Battle Room, House Wars, Events). A parent's login is a
 *       STUDENT-owner-type session (see OtpService.resolveOwner) - there is no separate Parent
 *       entity in this codebase.</li>
 *   <li>STUDENT &lt;-&gt; STUDENT, where both are in the same class-section (classmates).</li>
 * </ul>
 */
@Service
public class CallAuthorizationService {

	private final EmployeeRepository employeeRepository;
	private final StudentRepository studentRepository;
	private final CredentialRepository credentialRepository;
	private final SectionSubjectTeacherRepository sectionSubjectTeacherRepository;

	public CallAuthorizationService(
			EmployeeRepository employeeRepository,
			StudentRepository studentRepository,
			CredentialRepository credentialRepository,
			SectionSubjectTeacherRepository sectionSubjectTeacherRepository) {
		this.employeeRepository = employeeRepository;
		this.studentRepository = studentRepository;
		this.credentialRepository = credentialRepository;
		this.sectionSubjectTeacherRepository = sectionSubjectTeacherRepository;
	}

	public void requireCanCall(AuthPrincipal principal, OwnerType otherType, UUID otherId) {
		if (!canCall(principal.getSchoolId(), principal.getOwnerType(), principal.getOwnerId(), otherType, otherId)) {
			throw new IllegalArgumentException("You are not allowed to call this person");
		}
	}

	/**
	 * readOnly transactional so isClassTeacherOf's lazy Student.classSection/ClassSection.classTeacher
	 * dereference is safe even if a future caller invokes this outside an already-open transaction
	 * (today's callers all happen to be @Transactional themselves, but that's caller discipline this
	 * method shouldn't depend on).
	 */
	@Transactional(readOnly = true)
	public boolean canCall(UUID schoolId, OwnerType callerType, UUID callerId, OwnerType otherType, UUID otherId) {
		if (callerType == otherType && callerId.equals(otherId)) {
			return false;
		}
		if (callerType == OwnerType.STUDENT && otherType == OwnerType.STUDENT) {
			return isSameClassSection(schoolId, callerId, otherId);
		}
		if (callerType == OwnerType.EMPLOYEE && otherType == OwnerType.EMPLOYEE) {
			return isAdmin(otherId) || isAdmin(callerId);
		}
		UUID studentId = callerType == OwnerType.STUDENT ? callerId : otherId;
		UUID employeeId = callerType == OwnerType.EMPLOYEE ? callerId : otherId;
		return isAdmin(employeeId)
				|| isClassTeacherOf(schoolId, studentId, employeeId)
				|| isSubjectTeacherOf(schoolId, studentId, employeeId);
	}

	private boolean isAdmin(UUID employeeId) {
		return credentialRepository.findByOwnerTypeAndOwnerId(OwnerType.EMPLOYEE, employeeId)
				.map(credential -> credential.getRole() == Role.ADMIN)
				.orElse(false);
	}

	private boolean isClassTeacherOf(UUID schoolId, UUID studentId, UUID employeeId) {
		Student student = studentRepository.findByIdAndSchoolId(studentId, schoolId).orElse(null);
		if (student == null || student.getClassSection() == null || student.getClassSection().getClassTeacher() == null) {
			return false;
		}
		return student.getClassSection().getClassTeacher().getId().equals(employeeId);
	}

	private boolean isSubjectTeacherOf(UUID schoolId, UUID studentId, UUID employeeId) {
		Student student = studentRepository.findByIdAndSchoolId(studentId, schoolId).orElse(null);
		if (student == null || student.getClassSection() == null) {
			return false;
		}
		return sectionSubjectTeacherRepository.existsBySectionIdAndTeacherId(student.getClassSection().getId(), employeeId);
	}

	private boolean isSameClassSection(UUID schoolId, UUID studentId, UUID otherStudentId) {
		Student student = studentRepository.findByIdAndSchoolId(studentId, schoolId).orElse(null);
		Student other = studentRepository.findByIdAndSchoolId(otherStudentId, schoolId).orElse(null);
		if (student == null || other == null || student.getClassSection() == null || other.getClassSection() == null) {
			return false;
		}
		return student.getClassSection().getId().equals(other.getClassSection().getId());
	}

	/** Throws if {@code otherType}/{@code otherId} doesn't exist in this school - checked before authz. */
	public void requireExists(UUID schoolId, OwnerType ownerType, UUID ownerId) {
		boolean exists = ownerType == OwnerType.EMPLOYEE
				? employeeRepository.findByIdAndSchoolId(ownerId, schoolId).isPresent()
				: studentRepository.findByIdAndSchoolId(ownerId, schoolId).isPresent();
		if (!exists) {
			throw new EntityNotFoundException((ownerType == OwnerType.EMPLOYEE ? "Employee" : "Student") + " not found");
		}
	}

}
