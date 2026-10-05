package com.gurukul.calls.service;

import com.gurukul.academics.repository.SectionSubjectTeacherRepository;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.repository.CredentialRepository;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.parents.entity.ParentStudentLink;
import com.gurukul.parents.repository.ParentRepository;
import com.gurukul.parents.repository.ParentStudentLinkRepository;
import com.gurukul.students.entity.Student;
import com.gurukul.students.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
 *       everywhere else - Arena, Battle Room, House Wars, Events).</li>
 *   <li>PARENT &lt;-&gt; EMPLOYEE, where the EMPLOYEE is the class teacher or a subject teacher of
 *       one of the parent's linked children's sections, or an admin of the school - the student
 *       rule applied through each linked child.</li>
 *   <li>STUDENT &lt;-&gt; STUDENT, where both are in the same class-section (classmates).</li>
 * </ul>
 * Anything else - parent &lt;-&gt; parent, parent &lt;-&gt; student - is denied.
 */
@Service
public class CallAuthorizationService {

	private final EmployeeRepository employeeRepository;
	private final StudentRepository studentRepository;
	private final CredentialRepository credentialRepository;
	private final SectionSubjectTeacherRepository sectionSubjectTeacherRepository;
	private final ParentRepository parentRepository;
	private final ParentStudentLinkRepository parentStudentLinkRepository;

	public CallAuthorizationService(
			EmployeeRepository employeeRepository,
			StudentRepository studentRepository,
			CredentialRepository credentialRepository,
			SectionSubjectTeacherRepository sectionSubjectTeacherRepository,
			ParentRepository parentRepository,
			ParentStudentLinkRepository parentStudentLinkRepository) {
		this.employeeRepository = employeeRepository;
		this.studentRepository = studentRepository;
		this.credentialRepository = credentialRepository;
		this.sectionSubjectTeacherRepository = sectionSubjectTeacherRepository;
		this.parentRepository = parentRepository;
		this.parentStudentLinkRepository = parentStudentLinkRepository;
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
			return isAdmin(schoolId, otherId) || isAdmin(schoolId, callerId);
		}
		if (isPair(callerType, otherType, OwnerType.STUDENT, OwnerType.EMPLOYEE)) {
			UUID studentId = callerType == OwnerType.STUDENT ? callerId : otherId;
			UUID employeeId = callerType == OwnerType.EMPLOYEE ? callerId : otherId;
			return isAdmin(schoolId, employeeId) || teachesStudent(schoolId, studentId, employeeId);
		}
		if (isPair(callerType, otherType, OwnerType.PARENT, OwnerType.EMPLOYEE)) {
			UUID parentId = callerType == OwnerType.PARENT ? callerId : otherId;
			UUID employeeId = callerType == OwnerType.EMPLOYEE ? callerId : otherId;
			if (parentRepository.findByIdAndSchoolId(parentId, schoolId).isEmpty()) {
				return false;
			}
			if (isAdmin(schoolId, employeeId)) {
				return true;
			}
			List<ParentStudentLink> links = parentStudentLinkRepository.findAllByParentId(parentId);
			return links.stream().anyMatch(link -> teachesStudent(schoolId, link.getStudentId(), employeeId));
		}
		return false;
	}

	private static boolean isPair(OwnerType a, OwnerType b, OwnerType x, OwnerType y) {
		return (a == x && b == y) || (a == y && b == x);
	}

	private boolean teachesStudent(UUID schoolId, UUID studentId, UUID employeeId) {
		return isClassTeacherOf(schoolId, studentId, employeeId) || isSubjectTeacherOf(schoolId, studentId, employeeId);
	}

	/** Same school only - an admin credential from another school grants nothing here. */
	private boolean isAdmin(UUID schoolId, UUID employeeId) {
		return credentialRepository.findByOwnerTypeAndOwnerId(OwnerType.EMPLOYEE, employeeId)
				.map(credential -> credential.getRole() == Role.ADMIN && schoolId.equals(credential.getSchoolId()))
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
		if (ownerType == null) {
			throw new IllegalArgumentException("Owner type is required");
		}
		boolean exists = switch (ownerType) {
			case EMPLOYEE -> employeeRepository.findByIdAndSchoolId(ownerId, schoolId).isPresent();
			case STUDENT -> studentRepository.findByIdAndSchoolId(ownerId, schoolId).isPresent();
			case PARENT -> parentRepository.findByIdAndSchoolId(ownerId, schoolId).isPresent();
		};
		if (!exists) {
			String label = switch (ownerType) {
				case EMPLOYEE -> "Employee";
				case STUDENT -> "Student";
				case PARENT -> "Parent";
			};
			throw new EntityNotFoundException(label + " not found");
		}
	}

}
