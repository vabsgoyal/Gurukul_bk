package com.gurukul.registration.service;

import com.gurukul.auth.entity.Credential;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.google.GoogleTokenVerifier;
import com.gurukul.auth.google.GoogleTokenVerifier.GoogleIdentity;
import com.gurukul.auth.repository.CredentialRepository;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.employees.dto.EmployeeRequest;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.employees.service.EmployeeService;
import com.gurukul.parents.entity.Parent;
import com.gurukul.parents.entity.ParentStudentLink;
import com.gurukul.parents.repository.ParentRepository;
import com.gurukul.parents.repository.ParentStudentLinkRepository;
import com.gurukul.registration.dto.RegistrationDtos.LinkChildRequest;
import com.gurukul.registration.dto.RegistrationDtos.ParentGoogleRegistrationRequest;
import com.gurukul.registration.dto.RegistrationDtos.ParentRegistrationRequest;
import com.gurukul.registration.dto.RegistrationDtos.PendingRegistrationResponse;
import com.gurukul.registration.dto.RegistrationDtos.RegistrationSubmittedResponse;
import com.gurukul.registration.dto.RegistrationDtos.StudentGoogleRegistrationRequest;
import com.gurukul.registration.dto.RegistrationDtos.StudentRegistrationRequest;
import com.gurukul.registration.dto.RegistrationDtos.TeacherGoogleRegistrationRequest;
import com.gurukul.registration.dto.RegistrationDtos.TeacherRegistrationRequest;
import com.gurukul.registration.entity.TeacherInvite;
import com.gurukul.registration.repository.TeacherInviteRepository;
import com.gurukul.students.dto.StudentRequest;
import com.gurukul.students.entity.Student;
import com.gurukul.students.repository.StudentRepository;
import com.gurukul.students.service.StudentService;
import com.gurukul.workflow.entity.ApprovalRequest;
import com.gurukul.workflow.entity.ApprovalStatus;
import com.gurukul.workflow.repository.ApprovalRequestRepository;
import com.gurukul.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Self-registration for student/teacher/parent, gated by admin approval before login works.
 * Reuses the existing generic ApprovalRequest/WorkflowService (previously only used for finance
 * approvals) rather than a parallel status field, and reuses StudentService/EmployeeService's own
 * create() so a self-registered record is created exactly like an admin-entered one (same
 * validation, same defaults). The one piece those don't do: the Credential is created disabled and
 * only enabled on approval, so nothing can log in until an admin says so.
 */
@Service
@RequiredArgsConstructor
public class RegistrationService {

	public static final String STUDENT_REGISTRATION = "STUDENT_REGISTRATION";
	public static final String EMPLOYEE_REGISTRATION = "EMPLOYEE_REGISTRATION";
	public static final String PARENT_REGISTRATION = "PARENT_REGISTRATION";

	private final StudentService studentService;
	private final StudentRepository studentRepository;
	private final EmployeeService employeeService;
	private final EmployeeRepository employeeRepository;
	private final ParentRepository parentRepository;
	private final ParentStudentLinkRepository parentStudentLinkRepository;
	private final TeacherInviteRepository teacherInviteRepository;
	private final CredentialRepository credentialRepository;
	private final ApprovalRequestRepository approvalRequestRepository;
	private final WorkflowService workflowService;
	private final PasswordEncoder passwordEncoder;
	private final SchoolContext schoolContext;
	private final GoogleTokenVerifier googleTokenVerifier;

	@Transactional
	public RegistrationSubmittedResponse registerStudent(StudentRegistrationRequest request) {
		StudentRequest studentRequest = new StudentRequest();
		studentRequest.setRollNumber(request.getRollNumber());
		studentRequest.setName(request.getName());
		studentRequest.setDob(request.getDob());
		studentRequest.setGender(request.getGender());
		studentRequest.setAddress(request.getAddress());
		studentRequest.setParentName(request.getParentName());
		studentRequest.setParentContact(request.getParentContact());
		studentRequest.setClassSectionId(request.getClassSectionId());
		studentRequest.setAdmissionDate(request.getAdmissionDate());

		Student student = studentService.createEntity(studentRequest);
		createDisabledCredential(OwnerType.STUDENT, student.getId(), Role.STUDENT, request.getUsername(), request.getPassword());
		submit(STUDENT_REGISTRATION, student.getId(), request.getUsername());
		return new RegistrationSubmittedResponse(student.getId(), "Registration submitted - pending admin approval");
	}

	@Transactional
	public RegistrationSubmittedResponse registerTeacher(TeacherRegistrationRequest request) {
		UUID schoolId = schoolContext.getSchoolId();
		TeacherInvite invite = teacherInviteRepository.findBySchoolIdAndCode(schoolId, request.getInviteCode())
				.orElseThrow(() -> new IllegalArgumentException("Invalid invite code"));
		if (invite.isUsed()) {
			throw new IllegalArgumentException("This invite code has already been used");
		}
		if (invite.getExpiresAt().isBefore(Instant.now())) {
			throw new IllegalArgumentException("This invite code has expired");
		}

		EmployeeRequest employeeRequest = new EmployeeRequest();
		employeeRequest.setName(request.getName());
		employeeRequest.setDesignation(request.getDesignation());
		employeeRequest.setJoinDate(request.getJoinDate());
		employeeRequest.setContactPhone(request.getContactPhone());
		employeeRequest.setContactEmail(request.getContactEmail());

		Employee employee = employeeService.createEntity(employeeRequest);
		createDisabledCredential(OwnerType.EMPLOYEE, employee.getId(), Role.TEACHER, request.getUsername(), request.getPassword());

		invite.setUsed(true);
		teacherInviteRepository.save(invite);

		submit(EMPLOYEE_REGISTRATION, employee.getId(), request.getUsername());
		return new RegistrationSubmittedResponse(employee.getId(), "Registration submitted - pending admin approval");
	}

	@Transactional
	public RegistrationSubmittedResponse registerParent(ParentRegistrationRequest request) {
		UUID schoolId = schoolContext.getSchoolId();
		Student student = studentRepository.findBySchoolIdAndRollNumber(schoolId, request.getStudentRollNumber())
				.orElseThrow(() -> new EntityNotFoundException("No student found with that roll number"));

		Parent parent = new Parent();
		parent.setSchoolId(schoolId);
		parent.setName(request.getName());
		parent.setEmail(request.getEmail());
		parent.setPhone(request.getPhone());
		parent = parentRepository.save(parent);

		ParentStudentLink link = new ParentStudentLink();
		link.setSchoolId(schoolId);
		link.setParentId(parent.getId());
		link.setStudentId(student.getId());
		parentStudentLinkRepository.save(link);

		createDisabledCredential(OwnerType.PARENT, parent.getId(), Role.PARENT, request.getUsername(), request.getPassword());
		submit(PARENT_REGISTRATION, parent.getId(), request.getUsername());
		return new RegistrationSubmittedResponse(parent.getId(), "Registration submitted - pending admin approval");
	}

	@Transactional
	public RegistrationSubmittedResponse registerStudentViaGoogle(StudentGoogleRegistrationRequest request) {
		GoogleIdentity identity = googleTokenVerifier.verify(request.getIdToken());

		StudentRequest studentRequest = new StudentRequest();
		studentRequest.setRollNumber(request.getRollNumber());
		studentRequest.setName(request.getName());
		studentRequest.setDob(request.getDob());
		studentRequest.setGender(request.getGender());
		studentRequest.setAddress(request.getAddress());
		studentRequest.setParentName(request.getParentName());
		studentRequest.setParentContact(request.getParentContact());
		studentRequest.setClassSectionId(request.getClassSectionId());
		studentRequest.setAdmissionDate(request.getAdmissionDate());

		Student student = studentService.createEntity(studentRequest);
		createDisabledGoogleCredential(OwnerType.STUDENT, student.getId(), Role.STUDENT, identity);
		submit(STUDENT_REGISTRATION, student.getId(), identity.email());
		return new RegistrationSubmittedResponse(student.getId(), "Registration submitted - pending admin approval");
	}

	@Transactional
	public RegistrationSubmittedResponse registerTeacherViaGoogle(TeacherGoogleRegistrationRequest request) {
		GoogleIdentity identity = googleTokenVerifier.verify(request.getIdToken());

		UUID schoolId = schoolContext.getSchoolId();
		TeacherInvite invite = teacherInviteRepository.findBySchoolIdAndCode(schoolId, request.getInviteCode())
				.orElseThrow(() -> new IllegalArgumentException("Invalid invite code"));
		if (invite.isUsed()) {
			throw new IllegalArgumentException("This invite code has already been used");
		}
		if (invite.getExpiresAt().isBefore(Instant.now())) {
			throw new IllegalArgumentException("This invite code has expired");
		}

		EmployeeRequest employeeRequest = new EmployeeRequest();
		employeeRequest.setName(request.getName());
		employeeRequest.setDesignation(request.getDesignation());
		employeeRequest.setJoinDate(request.getJoinDate());
		employeeRequest.setContactPhone(request.getContactPhone());
		employeeRequest.setContactEmail(identity.email());

		Employee employee = employeeService.createEntity(employeeRequest);
		createDisabledGoogleCredential(OwnerType.EMPLOYEE, employee.getId(), Role.TEACHER, identity);

		invite.setUsed(true);
		teacherInviteRepository.save(invite);

		submit(EMPLOYEE_REGISTRATION, employee.getId(), identity.email());
		return new RegistrationSubmittedResponse(employee.getId(), "Registration submitted - pending admin approval");
	}

	@Transactional
	public RegistrationSubmittedResponse registerParentViaGoogle(ParentGoogleRegistrationRequest request) {
		GoogleIdentity identity = googleTokenVerifier.verify(request.getIdToken());

		UUID schoolId = schoolContext.getSchoolId();
		Student student = studentRepository.findBySchoolIdAndRollNumber(schoolId, request.getStudentRollNumber())
				.orElseThrow(() -> new EntityNotFoundException("No student found with that roll number"));

		Parent parent = new Parent();
		parent.setSchoolId(schoolId);
		parent.setName(request.getName());
		parent.setEmail(identity.email());
		parent.setPhone(request.getPhone());
		parent = parentRepository.save(parent);

		ParentStudentLink link = new ParentStudentLink();
		link.setSchoolId(schoolId);
		link.setParentId(parent.getId());
		link.setStudentId(student.getId());
		parentStudentLinkRepository.save(link);

		createDisabledGoogleCredential(OwnerType.PARENT, parent.getId(), Role.PARENT, identity);
		submit(PARENT_REGISTRATION, parent.getId(), identity.email());
		return new RegistrationSubmittedResponse(parent.getId(), "Registration submitted - pending admin approval");
	}

	/**
	 * An already-approved parent linking another child (e.g. a sibling enrolling later) - no
	 * further approval needed, since the parent identity itself is already trusted; only the
	 * student-roll-number lookup needs to succeed.
	 */
	@Transactional
	public void linkAdditionalChild(UUID parentId, LinkChildRequest request) {
		UUID schoolId = schoolContext.getSchoolId();
		Student student = studentRepository.findBySchoolIdAndRollNumber(schoolId, request.getStudentRollNumber())
				.orElseThrow(() -> new EntityNotFoundException("No student found with that roll number"));
		if (parentStudentLinkRepository.existsByParentIdAndStudentId(parentId, student.getId())) {
			throw new IllegalArgumentException("This child is already linked to your account");
		}
		ParentStudentLink link = new ParentStudentLink();
		link.setSchoolId(schoolId);
		link.setParentId(parentId);
		link.setStudentId(student.getId());
		parentStudentLinkRepository.save(link);
	}

	@Transactional(readOnly = true)
	public List<PendingRegistrationResponse> listPending(String entityType) {
		UUID schoolId = schoolContext.getSchoolId();
		return approvalRequestRepository
				.findAllBySchoolIdAndEntityTypeAndStatusOrderByCreatedAtAsc(schoolId, entityType, ApprovalStatus.SUBMITTED)
				.stream()
				.map(r -> new PendingRegistrationResponse(
						r.getEntityId(), r.getEntityType(), displayNameFor(entityType, r.getEntityId(), schoolId),
						r.getSubmittedBy(), r.getCreatedAt()))
				.toList();
	}

	@Transactional
	public void approve(String entityType, UUID entityId, String approvedBy, String comment) {
		workflowService.approve(entityType, entityId, approvedBy, comment);
		Credential credential = credentialRepository
				.findByOwnerTypeAndOwnerId(ownerTypeFor(entityType), entityId)
				.orElseThrow(() -> new EntityNotFoundException("Credential not found for this registration"));
		credential.setEnabled(true);
		credentialRepository.save(credential);
	}

	@Transactional
	public void reject(String entityType, UUID entityId, String rejectedBy, String comment) {
		workflowService.reject(entityType, entityId, rejectedBy, comment);
	}

	private void submit(String entityType, UUID entityId, String submittedBy) {
		workflowService.submit(entityType, entityId, submittedBy);
	}

	private void createDisabledCredential(OwnerType ownerType, UUID ownerId, Role role, String username, String password) {
		UUID schoolId = schoolContext.getSchoolId();
		if (credentialRepository.existsBySchoolIdAndUsername(schoolId, username)) {
			throw new IllegalArgumentException("Username already taken for this school");
		}
		Credential credential = new Credential();
		credential.setSchoolId(schoolId);
		credential.setOwnerType(ownerType);
		credential.setOwnerId(ownerId);
		credential.setUsername(username);
		credential.setPasswordHash(passwordEncoder.encode(password));
		credential.setRole(role);
		credential.setEnabled(false);
		credentialRepository.save(credential);
	}

	/**
	 * Google email must already be verified by Google itself (payload's email_verified claim) -
	 * we don't do our own verification email, so this is the only integrity check available.
	 * Username is set to the verified email; password is a random, never-shared value so the
	 * password-login path simply can never match it - this account only ever logs in via Google.
	 */
	private void createDisabledGoogleCredential(OwnerType ownerType, UUID ownerId, Role role, GoogleIdentity identity) {
		if (!identity.emailVerified()) {
			throw new IllegalArgumentException("Your Google account's email isn't verified");
		}
		UUID schoolId = schoolContext.getSchoolId();
		if (credentialRepository.existsBySchoolIdAndUsername(schoolId, identity.email())) {
			throw new IllegalArgumentException("An account already exists for this Google email at this school");
		}
		if (credentialRepository.findBySchoolIdAndGoogleSubject(schoolId, identity.subject()).isPresent()) {
			throw new IllegalArgumentException("This Google account is already registered at this school");
		}
		Credential credential = new Credential();
		credential.setSchoolId(schoolId);
		credential.setOwnerType(ownerType);
		credential.setOwnerId(ownerId);
		credential.setUsername(identity.email());
		credential.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
		credential.setRole(role);
		credential.setGoogleSubject(identity.subject());
		credential.setEnabled(false);
		credentialRepository.save(credential);
	}

	private OwnerType ownerTypeFor(String entityType) {
		return switch (entityType) {
			case STUDENT_REGISTRATION -> OwnerType.STUDENT;
			case EMPLOYEE_REGISTRATION -> OwnerType.EMPLOYEE;
			case PARENT_REGISTRATION -> OwnerType.PARENT;
			default -> throw new IllegalArgumentException("Unknown registration entity type: " + entityType);
		};
	}

	private String displayNameFor(String entityType, UUID entityId, UUID schoolId) {
		return switch (entityType) {
			case STUDENT_REGISTRATION -> studentRepository.findByIdAndSchoolId(entityId, schoolId)
					.map(Student::getName).orElse("Unknown student");
			case EMPLOYEE_REGISTRATION -> employeeRepository.findByIdAndSchoolId(entityId, schoolId)
					.map(Employee::getName).orElse("Unknown employee");
			case PARENT_REGISTRATION -> parentRepository.findByIdAndSchoolId(entityId, schoolId)
					.map(Parent::getName).orElse("Unknown parent");
			default -> "Unknown";
		};
	}

}
