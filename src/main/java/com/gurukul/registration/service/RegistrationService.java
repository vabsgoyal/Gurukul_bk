package com.gurukul.registration.service;

import com.gurukul.auth.entity.Credential;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.google.GoogleTokenVerifier;
import com.gurukul.auth.google.GoogleTokenVerifier.GoogleIdentity;
import com.gurukul.auth.repository.CredentialRepository;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.repository.EmployeeRepository;
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
import com.gurukul.registration.dto.RegistrationDtos.StudentInviteGoogleRegistrationRequest;
import com.gurukul.registration.dto.RegistrationDtos.StudentInviteRegistrationRequest;
import com.gurukul.registration.dto.RegistrationDtos.StudentRegistrationRequest;
import com.gurukul.registration.dto.RegistrationDtos.TeacherGoogleRegistrationRequest;
import com.gurukul.registration.dto.RegistrationDtos.TeacherRegistrationRequest;
import com.gurukul.registration.entity.StudentInvite;
import com.gurukul.registration.entity.TeacherInvite;
import com.gurukul.registration.repository.StudentInviteRepository;
import com.gurukul.registration.repository.TeacherInviteRepository;
import com.gurukul.students.entity.Student;
import com.gurukul.students.repository.StudentRepository;
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
 * Self-registration for student/teacher/parent - claims an existing, admin-created record (Student
 * at admission, Employee at hiring) rather than creating a new one, so the person never re-enters
 * data an admin already captured. Student and teacher claims auto-activate immediately: the admin
 * already vetted the underlying record's existence (it can only be claimed once - see the
 * already-has-a-credential checks below), so the only remaining question is "does this login belong
 * to that record," which registrationNumber/inviteCode already answer. Parent claims are weaker
 * (roll number + phone are not secret) so they stay approval-gated via the existing generic
 * ApprovalRequest/WorkflowService, plus rate-limiting on failed parentContact guesses.
 */
@Service
@RequiredArgsConstructor
public class RegistrationService {

	public static final String STUDENT_REGISTRATION = "STUDENT_REGISTRATION";
	public static final String EMPLOYEE_REGISTRATION = "EMPLOYEE_REGISTRATION";
	public static final String PARENT_REGISTRATION = "PARENT_REGISTRATION";

	private final StudentRepository studentRepository;
	private final EmployeeRepository employeeRepository;
	private final ParentRepository parentRepository;
	private final ParentStudentLinkRepository parentStudentLinkRepository;
	private final TeacherInviteRepository teacherInviteRepository;
	private final StudentInviteRepository studentInviteRepository;
	private final ParentClaimRateLimiter parentClaimRateLimiter;
	private final CredentialRepository credentialRepository;
	private final ApprovalRequestRepository approvalRequestRepository;
	private final WorkflowService workflowService;
	private final PasswordEncoder passwordEncoder;
	private final SchoolContext schoolContext;
	private final GoogleTokenVerifier googleTokenVerifier;

	@Transactional
	public RegistrationSubmittedResponse registerStudent(StudentRegistrationRequest request) {
		Student student = findClaimableStudent(request.getRegistrationNumber());
		createEnabledCredential(OwnerType.STUDENT, student.getId(), Role.STUDENT, request.getUsername(), request.getPassword());
		return new RegistrationSubmittedResponse(student.getId(), "Registration complete - you can log in now");
	}

	@Transactional
	public RegistrationSubmittedResponse registerTeacher(TeacherRegistrationRequest request) {
		TeacherInvite invite = consumeInvite(request.getInviteCode());
		createEnabledCredential(OwnerType.EMPLOYEE, invite.getTargetEmployeeId(), Role.TEACHER, request.getUsername(), request.getPassword());
		return new RegistrationSubmittedResponse(invite.getTargetEmployeeId(), "Registration complete - you can log in now");
	}

	@Transactional
	public RegistrationSubmittedResponse registerStudentViaInvite(StudentInviteRegistrationRequest request) {
		StudentInvite invite = consumeStudentInvite(request.getInviteCode());
		createEnabledCredential(OwnerType.STUDENT, invite.getTargetStudentId(), Role.STUDENT, request.getUsername(), request.getPassword());
		return new RegistrationSubmittedResponse(invite.getTargetStudentId(), "Registration complete - you can log in now");
	}

	@Transactional
	public RegistrationSubmittedResponse registerParent(ParentRegistrationRequest request) {
		UUID schoolId = schoolContext.getSchoolId();
		Student student = verifyParentClaim(request.getStudentRegistrationNumber(), request.getParentContact());

		Parent parent = new Parent();
		parent.setSchoolId(schoolId);
		parent.setName(student.getParentName());
		parent.setPhone(student.getParentContact());
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
		Student student = findClaimableStudent(request.getRegistrationNumber());
		createEnabledGoogleCredential(OwnerType.STUDENT, student.getId(), Role.STUDENT, identity);
		return new RegistrationSubmittedResponse(student.getId(), "Registration complete - you can log in now");
	}

	@Transactional
	public RegistrationSubmittedResponse registerTeacherViaGoogle(TeacherGoogleRegistrationRequest request) {
		GoogleIdentity identity = googleTokenVerifier.verify(request.getIdToken());
		TeacherInvite invite = consumeInvite(request.getInviteCode());
		createEnabledGoogleCredential(OwnerType.EMPLOYEE, invite.getTargetEmployeeId(), Role.TEACHER, identity);
		return new RegistrationSubmittedResponse(invite.getTargetEmployeeId(), "Registration complete - you can log in now");
	}

	@Transactional
	public RegistrationSubmittedResponse registerStudentViaInviteGoogle(StudentInviteGoogleRegistrationRequest request) {
		GoogleIdentity identity = googleTokenVerifier.verify(request.getIdToken());
		StudentInvite invite = consumeStudentInvite(request.getInviteCode());
		createEnabledGoogleCredential(OwnerType.STUDENT, invite.getTargetStudentId(), Role.STUDENT, identity);
		return new RegistrationSubmittedResponse(invite.getTargetStudentId(), "Registration complete - you can log in now");
	}

	@Transactional
	public RegistrationSubmittedResponse registerParentViaGoogle(ParentGoogleRegistrationRequest request) {
		GoogleIdentity identity = googleTokenVerifier.verify(request.getIdToken());
		UUID schoolId = schoolContext.getSchoolId();
		Student student = verifyParentClaim(request.getStudentRegistrationNumber(), request.getParentContact());

		Parent parent = new Parent();
		parent.setSchoolId(schoolId);
		parent.setName(student.getParentName());
		parent.setEmail(identity.email());
		parent.setPhone(student.getParentContact());
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

	private Student findClaimableStudent(String registrationNumber) {
		UUID schoolId = schoolContext.getSchoolId();
		Student student = studentRepository.findBySchoolIdAndRegistrationNumber(schoolId, registrationNumber)
				.orElseThrow(() -> new EntityNotFoundException("No student found with that registration number"));
		if (credentialRepository.findByOwnerTypeAndOwnerId(OwnerType.STUDENT, student.getId()).isPresent()) {
			throw new IllegalArgumentException("This student has already claimed a login");
		}
		return student;
	}

	private TeacherInvite consumeInvite(String code) {
		UUID schoolId = schoolContext.getSchoolId();
		TeacherInvite invite = teacherInviteRepository.findBySchoolIdAndCode(schoolId, code)
				.orElseThrow(() -> new IllegalArgumentException("Invalid invite code"));
		if (invite.isUsed()) {
			throw new IllegalArgumentException("This invite code has already been used");
		}
		if (invite.getExpiresAt().isBefore(Instant.now())) {
			throw new IllegalArgumentException("This invite code has expired");
		}
		if (invite.getTargetEmployeeId() == null) {
			throw new IllegalArgumentException("This invite code is not tied to an employee record");
		}
		if (credentialRepository.findByOwnerTypeAndOwnerId(OwnerType.EMPLOYEE, invite.getTargetEmployeeId()).isPresent()) {
			throw new IllegalArgumentException("This employee has already claimed a login");
		}
		invite.setUsed(true);
		teacherInviteRepository.save(invite);
		return invite;
	}

	private StudentInvite consumeStudentInvite(String code) {
		UUID schoolId = schoolContext.getSchoolId();
		StudentInvite invite = studentInviteRepository.findBySchoolIdAndCode(schoolId, code)
				.orElseThrow(() -> new IllegalArgumentException("Invalid invite code"));
		if (invite.isUsed()) {
			throw new IllegalArgumentException("This invite code has already been used");
		}
		if (invite.getExpiresAt().isBefore(Instant.now())) {
			throw new IllegalArgumentException("This invite code has expired");
		}
		if (credentialRepository.findByOwnerTypeAndOwnerId(OwnerType.STUDENT, invite.getTargetStudentId()).isPresent()) {
			throw new IllegalArgumentException("This student has already claimed a login");
		}
		invite.setUsed(true);
		studentInviteRepository.save(invite);
		return invite;
	}

	/**
	 * Rate-limits guessing parentContact, since a registrationNumber + phone number are not secret
	 * data. Uses registrationNumber (not rollNumber) as the claim key - rollNumber is only unique
	 * within a class-section (server-computed alphabetical rank) and can change as classmates
	 * join/leave, so it can't safely identify a specific student on its own.
	 * Delegates the attempt-counter writes to ParentClaimRateLimiter's own REQUIRES_NEW transaction -
	 * this method throws on every failure, which would otherwise roll back the increment right along
	 * with the rest of this transaction and silently defeat the rate limit.
	 */
	private Student verifyParentClaim(String studentRegistrationNumber, String parentContact) {
		UUID schoolId = schoolContext.getSchoolId();
		if (parentClaimRateLimiter.isLocked(schoolId, studentRegistrationNumber)) {
			throw new IllegalArgumentException("Too many failed attempts - try again later");
		}

		Student student = studentRepository.findBySchoolIdAndRegistrationNumber(schoolId, studentRegistrationNumber)
				.orElseThrow(() -> new EntityNotFoundException("No student found with that registration number"));

		if (!student.getParentContact().equals(parentContact)) {
			parentClaimRateLimiter.recordFailure(schoolId, studentRegistrationNumber);
			throw new IllegalArgumentException("Registration number and parent contact do not match our records");
		}

		parentClaimRateLimiter.recordSuccess(schoolId, studentRegistrationNumber);
		return student;
	}

	/**
	 * An already-approved parent linking another child (e.g. a sibling enrolling later) - no
	 * further approval needed, since the parent identity itself is already trusted; only the
	 * registrationNumber lookup needs to succeed.
	 */
	@Transactional
	public void linkAdditionalChild(UUID parentId, LinkChildRequest request) {
		UUID schoolId = schoolContext.getSchoolId();
		Student student = studentRepository.findBySchoolIdAndRegistrationNumber(schoolId, request.getStudentRegistrationNumber())
				.orElseThrow(() -> new EntityNotFoundException("No student found with that registration number"));
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
		Credential credential = buildCredential(ownerType, ownerId, role, username, password);
		credential.setEnabled(false);
		credentialRepository.save(credential);
	}

	/** Student/teacher claims auto-activate immediately - admin already vetted the claimed record's existence. */
	private void createEnabledCredential(OwnerType ownerType, UUID ownerId, Role role, String username, String password) {
		Credential credential = buildCredential(ownerType, ownerId, role, username, password);
		credential.setEnabled(true);
		credentialRepository.save(credential);
	}

	private Credential buildCredential(OwnerType ownerType, UUID ownerId, Role role, String username, String password) {
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
		return credential;
	}

	/**
	 * Google email must already be verified by Google itself (payload's email_verified claim) -
	 * we don't do our own verification email, so this is the only integrity check available.
	 * Username is set to the verified email; password is a random, never-shared value so the
	 * password-login path simply can never match it - this account only ever logs in via Google.
	 */
	private void createDisabledGoogleCredential(OwnerType ownerType, UUID ownerId, Role role, GoogleIdentity identity) {
		Credential credential = buildGoogleCredential(ownerType, ownerId, role, identity);
		credential.setEnabled(false);
		credentialRepository.save(credential);
	}

	/** Student/teacher claims auto-activate immediately - admin already vetted the claimed record's existence. */
	private void createEnabledGoogleCredential(OwnerType ownerType, UUID ownerId, Role role, GoogleIdentity identity) {
		Credential credential = buildGoogleCredential(ownerType, ownerId, role, identity);
		credential.setEnabled(true);
		credentialRepository.save(credential);
	}

	private Credential buildGoogleCredential(OwnerType ownerType, UUID ownerId, Role role, GoogleIdentity identity) {
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
		return credential;
	}

	private OwnerType ownerTypeFor(String entityType) {
		return switch (entityType) {
			case STUDENT_REGISTRATION -> OwnerType.STUDENT;
			case EMPLOYEE_REGISTRATION -> OwnerType.EMPLOYEE;
			case PARENT_REGISTRATION -> OwnerType.PARENT;
			default -> throw new IllegalArgumentException("Unknown registration entity type: " + entityType);
		};
	}

	private String entityTypeFor(OwnerType ownerType) {
		return switch (ownerType) {
			case STUDENT -> STUDENT_REGISTRATION;
			case EMPLOYEE -> EMPLOYEE_REGISTRATION;
			case PARENT -> PARENT_REGISTRATION;
		};
	}

	/** Used by AuthService to give a distinct login error for "rejected" vs "still pending". */
	@Transactional(readOnly = true)
	public ApprovalStatus findApprovalStatus(OwnerType ownerType, UUID ownerId) {
		return approvalRequestRepository.findByEntityTypeAndEntityId(entityTypeFor(ownerType), ownerId)
				.map(ApprovalRequest::getStatus)
				.orElse(ApprovalStatus.SUBMITTED);
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
