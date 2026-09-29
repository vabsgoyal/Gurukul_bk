package com.gurukul.chat.service;

import com.gurukul.academics.entity.SectionSubjectTeacher;
import com.gurukul.academics.repository.SectionSubjectTeacherRepository;
import com.gurukul.auth.entity.Credential;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.repository.CredentialRepository;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.chat.dto.ChatDtos.ContactResponse;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.parents.entity.ParentStudentLink;
import com.gurukul.parents.repository.ParentRepository;
import com.gurukul.parents.repository.ParentStudentLinkRepository;
import com.gurukul.students.entity.ClassSection;
import com.gurukul.students.entity.Student;
import com.gurukul.students.entity.StudentStatus;
import com.gurukul.students.repository.ClassSectionRepository;
import com.gurukul.students.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The one rule for who may chat with a parent, used both to authorize starting a conversation
 * (ConversationService.createOneToOne) and to list who the app offers (GET /api/v1/chat/contacts),
 * so the two can never disagree.
 *
 * <p>A parent and an employee may pair when the employee is an (enabled) ADMIN of the school, or
 * teaches a section one of the parent's linked, currently enrolled (ACTIVE) children is in - as that
 * section's class teacher or through any subject assignment (section_subject_teacher). A TEACHER may
 * start with exactly those parents; an ADMIN with any parent of the school. Nobody else (students,
 * other parents) pairs with a parent.
 *
 * <p>Like ConversationService, takes an explicit AuthPrincipal / schoolId, never the request
 * ThreadLocals, so it works from a STOMP thread too.
 */
@Service
@RequiredArgsConstructor
public class ChatContactService {

	private final ParentStudentLinkRepository parentStudentLinkRepository;
	private final ParentRepository parentRepository;
	private final StudentRepository studentRepository;
	private final ClassSectionRepository classSectionRepository;
	private final SectionSubjectTeacherRepository sectionSubjectTeacherRepository;
	private final CredentialRepository credentialRepository;
	private final EmployeeRepository employeeRepository;

	/** Whether this parent may start a chat with this employee. */
	@Transactional(readOnly = true)
	public boolean parentMayContactEmployee(UUID schoolId, UUID parentId, UUID employeeId) {
		return isAdmin(schoolId, employeeId) || teachesAChildOf(schoolId, employeeId, parentId);
	}

	/** Whether this staff member may start a chat with this parent. */
	@Transactional(readOnly = true)
	public boolean staffMayContactParent(AuthPrincipal staff, UUID parentId) {
		if (staff.getOwnerType() != OwnerType.EMPLOYEE) {
			return false;
		}
		if (staff.getRole() == Role.ADMIN) {
			return parentRepository.findByIdAndSchoolId(parentId, staff.getSchoolId()).isPresent();
		}
		return staff.getRole() == Role.TEACHER && teachesAChildOf(staff.getSchoolId(), staff.getOwnerId(), parentId);
	}

	@Transactional(readOnly = true)
	public List<ContactResponse> contactsFor(AuthPrincipal principal) {
		UUID schoolId = principal.getSchoolId();
		return switch (principal.getRole()) {
			case PARENT -> staffContactsForParent(schoolId, principal.getOwnerId());
			case TEACHER -> parentContactsForTeacher(schoolId, principal.getOwnerId());
			case ADMIN -> parentContacts(schoolId, parentStudentLinkRepository.findAllBySchoolId(schoolId),
					studentRepository.findAllBySchoolId(schoolId));
			case STUDENT -> List.of();
		};
	}

	private boolean isAdmin(UUID schoolId, UUID employeeId) {
		return credentialRepository.findByOwnerTypeAndOwnerId(OwnerType.EMPLOYEE, employeeId)
				.filter(c -> c.getSchoolId().equals(schoolId) && c.getRole() == Role.ADMIN && c.isEnabled())
				.isPresent();
	}

	private boolean teachesAChildOf(UUID schoolId, UUID employeeId, UUID parentId) {
		Set<UUID> sectionIds = activeChildren(schoolId, parentId).stream()
				.map(s -> s.getClassSection().getId())
				.collect(Collectors.toSet());
		if (sectionIds.isEmpty()) {
			return false;
		}
		return classSectionRepository.existsByIdInAndClassTeacher_Id(sectionIds, employeeId)
				|| sectionSubjectTeacherRepository.existsBySectionIdInAndTeacherId(sectionIds, employeeId);
	}

	private List<Student> activeChildren(UUID schoolId, UUID parentId) {
		List<UUID> childIds = parentStudentLinkRepository.findAllByParentId(parentId).stream()
				.map(ParentStudentLink::getStudentId)
				.toList();
		if (childIds.isEmpty()) {
			return List.of();
		}
		return studentRepository.findAllBySchoolIdAndIdIn(schoolId, childIds).stream()
				.filter(s -> s.getStatus() == StudentStatus.ACTIVE)
				.toList();
	}

	private List<ContactResponse> staffContactsForParent(UUID schoolId, UUID parentId) {
		Map<UUID, ContactBuilder> byEmployee = new LinkedHashMap<>();
		Map<UUID, ClassSection> sections = activeChildren(schoolId, parentId).stream()
				.map(Student::getClassSection)
				.collect(Collectors.toMap(ClassSection::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

		for (ClassSection section : sections.values()) {
			Employee classTeacher = section.getClassTeacher();
			if (classTeacher != null) {
				builder(byEmployee, classTeacher).classTeacherOf.add(label(section));
			}
		}
		if (!sections.isEmpty()) {
			for (SectionSubjectTeacher assignment : sectionSubjectTeacherRepository.findAllBySectionIdIn(sections.keySet())) {
				builder(byEmployee, assignment.getTeacher()).subjects
						.add(assignment.getSubject().getName() + " (" + label(assignment.getSection()) + ")");
			}
		}
		List<UUID> adminIds = credentialRepository
				.findAllBySchoolIdAndOwnerTypeAndRole(schoolId, OwnerType.EMPLOYEE, Role.ADMIN).stream()
				.filter(Credential::isEnabled)
				.map(Credential::getOwnerId)
				.toList();
		if (!adminIds.isEmpty()) {
			for (Employee admin : employeeRepository.findAllBySchoolIdAndIdIn(schoolId, adminIds)) {
				builder(byEmployee, admin).admin = true;
			}
		}
		return byEmployee.values().stream()
				.map(ContactBuilder::build)
				.sorted(Comparator.comparing(ContactResponse::getName, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	private List<ContactResponse> parentContactsForTeacher(UUID schoolId, UUID teacherId) {
		Set<UUID> sectionIds = new LinkedHashSet<>();
		classSectionRepository.findAllBySchoolIdAndClassTeacherIdOrderByAcademicYearDesc(schoolId, teacherId)
				.forEach(s -> sectionIds.add(s.getId()));
		sectionSubjectTeacherRepository.findAllByTeacherId(teacherId).stream()
				.filter(a -> a.getSchoolId().equals(schoolId))
				.forEach(a -> sectionIds.add(a.getSection().getId()));
		if (sectionIds.isEmpty()) {
			return List.of();
		}
		List<Student> students = studentRepository.findAllBySchoolIdAndClassSectionIdIn(schoolId, sectionIds);
		if (students.isEmpty()) {
			return List.of();
		}
		List<ParentStudentLink> links = parentStudentLinkRepository.findAllBySchoolIdAndStudentIdIn(
				schoolId, students.stream().map(Student::getId).toList());
		return parentContacts(schoolId, links, students);
	}

	/** One entry per parent, naming whichever of the given (active) students are theirs. */
	private List<ContactResponse> parentContacts(UUID schoolId, List<ParentStudentLink> links, List<Student> students) {
		Map<UUID, Student> studentsById = students.stream()
				.filter(s -> s.getStatus() == StudentStatus.ACTIVE)
				.collect(Collectors.toMap(Student::getId, Function.identity()));
		Map<UUID, List<String>> childrenByParent = new LinkedHashMap<>();
		for (ParentStudentLink link : links) {
			Student child = studentsById.get(link.getStudentId());
			if (child != null) {
				childrenByParent.computeIfAbsent(link.getParentId(), id -> new ArrayList<>())
						.add(child.getName() + " (" + label(child.getClassSection()) + ")");
			}
		}
		if (childrenByParent.isEmpty()) {
			return List.of();
		}
		return parentRepository.findAllBySchoolIdAndIdIn(schoolId, childrenByParent.keySet()).stream()
				.map(p -> new ContactResponse(OwnerType.PARENT, p.getId(), p.getName(), false,
						List.of(), List.of(), childrenByParent.get(p.getId())))
				.sorted(Comparator.comparing(ContactResponse::getName, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	private static String label(ClassSection section) {
		return section.getClassName() + " - " + section.getSection();
	}

	private static ContactBuilder builder(Map<UUID, ContactBuilder> byEmployee, Employee employee) {
		return byEmployee.computeIfAbsent(employee.getId(), id -> new ContactBuilder(employee.getId(), employee.getName()));
	}

	private static final class ContactBuilder {
		private final UUID id;
		private final String name;
		private boolean admin;
		private final Set<String> classTeacherOf = new LinkedHashSet<>();
		private final Set<String> subjects = new LinkedHashSet<>();

		private ContactBuilder(UUID id, String name) {
			this.id = id;
			this.name = name;
		}

		private ContactResponse build() {
			return new ContactResponse(OwnerType.EMPLOYEE, id, name, admin,
					List.copyOf(classTeacherOf), List.copyOf(subjects), List.of());
		}
	}

}
