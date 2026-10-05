package com.gurukul.calls.service;

import com.gurukul.academics.repository.SectionSubjectTeacherRepository;
import com.gurukul.auth.entity.Credential;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.repository.CredentialRepository;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.parents.entity.Parent;
import com.gurukul.parents.entity.ParentStudentLink;
import com.gurukul.parents.repository.ParentRepository;
import com.gurukul.parents.repository.ParentStudentLinkRepository;
import com.gurukul.students.entity.ClassSection;
import com.gurukul.students.entity.Student;
import com.gurukul.students.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallAuthorizationServiceTest {

	private static final UUID SCHOOL = UUID.randomUUID();
	private static final UUID OTHER_SCHOOL = UUID.randomUUID();

	private final UUID parentId = UUID.randomUUID();
	private final UUID childId = UUID.randomUUID();
	private final UUID sectionId = UUID.randomUUID();
	private final UUID classTeacherId = UUID.randomUUID();
	private final UUID subjectTeacherId = UUID.randomUUID();
	private final UUID unrelatedTeacherId = UUID.randomUUID();
	private final UUID adminId = UUID.randomUUID();
	private final UUID otherSchoolAdminId = UUID.randomUUID();

	private EmployeeRepository employees;
	private StudentRepository students;
	private CredentialRepository credentials;
	private SectionSubjectTeacherRepository subjectTeachers;
	private ParentRepository parents;
	private ParentStudentLinkRepository links;
	private CallAuthorizationService service;

	@BeforeEach
	void setUp() {
		employees = mock(EmployeeRepository.class);
		students = mock(StudentRepository.class);
		credentials = mock(CredentialRepository.class);
		subjectTeachers = mock(SectionSubjectTeacherRepository.class);
		parents = mock(ParentRepository.class);
		links = mock(ParentStudentLinkRepository.class);
		service = new CallAuthorizationService(employees, students, credentials, subjectTeachers, parents, links);

		Parent parent = new Parent();
		parent.setId(parentId);
		parent.setSchoolId(SCHOOL);
		when(parents.findByIdAndSchoolId(parentId, SCHOOL)).thenReturn(Optional.of(parent));

		Employee classTeacher = new Employee();
		classTeacher.setId(classTeacherId);
		ClassSection section = new ClassSection();
		section.setId(sectionId);
		section.setClassTeacher(classTeacher);
		Student child = new Student();
		child.setId(childId);
		child.setClassSection(section);
		when(students.findByIdAndSchoolId(childId, SCHOOL)).thenReturn(Optional.of(child));

		ParentStudentLink link = new ParentStudentLink();
		link.setParentId(parentId);
		link.setStudentId(childId);
		when(links.findAllByParentId(parentId)).thenReturn(List.of(link));

		when(subjectTeachers.existsBySectionIdAndTeacherId(sectionId, subjectTeacherId)).thenReturn(true);

		when(credentials.findByOwnerTypeAndOwnerId(OwnerType.EMPLOYEE, adminId)).thenReturn(Optional.of(credential(SCHOOL, Role.ADMIN)));
		when(credentials.findByOwnerTypeAndOwnerId(OwnerType.EMPLOYEE, otherSchoolAdminId))
				.thenReturn(Optional.of(credential(OTHER_SCHOOL, Role.ADMIN)));
		when(credentials.findByOwnerTypeAndOwnerId(OwnerType.EMPLOYEE, unrelatedTeacherId))
				.thenReturn(Optional.of(credential(SCHOOL, Role.TEACHER)));
	}

	private static Credential credential(UUID schoolId, Role role) {
		Credential credential = new Credential();
		credential.setSchoolId(schoolId);
		credential.setRole(role);
		return credential;
	}

	private boolean parentCan(OwnerType type, UUID id) {
		return service.canCall(SCHOOL, OwnerType.PARENT, parentId, type, id);
	}

	@Test
	void parentCanCallTheirChildsClassTeacherSubjectTeacherAndOwnSchoolAdmin() {
		assertThat(parentCan(OwnerType.EMPLOYEE, classTeacherId)).isTrue();
		assertThat(parentCan(OwnerType.EMPLOYEE, subjectTeacherId)).isTrue();
		assertThat(parentCan(OwnerType.EMPLOYEE, adminId)).isTrue();
		// and the same pairs the other way round
		assertThat(service.canCall(SCHOOL, OwnerType.EMPLOYEE, classTeacherId, OwnerType.PARENT, parentId)).isTrue();
	}

	@Test
	void parentCannotCallAnUnrelatedTeacherOrAnotherSchoolsAdmin() {
		assertThat(parentCan(OwnerType.EMPLOYEE, unrelatedTeacherId)).isFalse();
		assertThat(parentCan(OwnerType.EMPLOYEE, otherSchoolAdminId)).isFalse();
		assertThat(service.canCall(SCHOOL, OwnerType.EMPLOYEE, unrelatedTeacherId, OwnerType.PARENT, parentId)).isFalse();
	}

	@Test
	void parentIsNeverTreatedAsAStudent() {
		// Parent <-> student/parent pairs have no rule - denied, not routed through the student checks.
		assertThat(parentCan(OwnerType.STUDENT, childId)).isFalse();
		assertThat(parentCan(OwnerType.PARENT, UUID.randomUUID())).isFalse();
		assertThat(service.canCall(SCHOOL, OwnerType.STUDENT, childId, OwnerType.PARENT, parentId)).isFalse();
		verify(students, never()).findByIdAndSchoolId(parentId, SCHOOL);
	}

	@Test
	void unknownOrOtherSchoolParentGetsNothingEvenFromAnAdmin() {
		UUID stranger = UUID.randomUUID();
		when(parents.findByIdAndSchoolId(stranger, SCHOOL)).thenReturn(Optional.empty());
		assertThat(service.canCall(SCHOOL, OwnerType.PARENT, stranger, OwnerType.EMPLOYEE, adminId)).isFalse();
	}

	@Test
	void requireExistsLooksParentsUpAsParents() {
		assertThatCode(() -> service.requireExists(SCHOOL, OwnerType.PARENT, parentId)).doesNotThrowAnyException();
		verify(students, never()).findByIdAndSchoolId(any(), any());

		UUID missing = UUID.randomUUID();
		when(parents.findByIdAndSchoolId(missing, SCHOOL)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.requireExists(SCHOOL, OwnerType.PARENT, missing))
				.isInstanceOf(EntityNotFoundException.class)
				.hasMessage("Parent not found");
	}

}
