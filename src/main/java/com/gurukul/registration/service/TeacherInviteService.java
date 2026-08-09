package com.gurukul.registration.service;

import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.registration.dto.RegistrationDtos.TeacherInviteResponse;
import com.gurukul.registration.entity.TeacherInvite;
import com.gurukul.registration.repository.TeacherInviteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeacherInviteService {

	private static final int CODE_LENGTH = 8;
	private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final long DEFAULT_VALIDITY_HOURS = 72;

	private final TeacherInviteRepository teacherInviteRepository;
	private final EmployeeRepository employeeRepository;
	private final SchoolContext schoolContext;

	/** Generates an invite tied to a specific, already-created Employee - the registering teacher just claims that record. */
	@Transactional
	public TeacherInviteResponse createInviteForEmployee(AuthPrincipal principal, UUID employeeId) {
		UUID schoolId = schoolContext.getSchoolId();
		if (employeeRepository.findByIdAndSchoolId(employeeId, schoolId).isEmpty()) {
			throw new EntityNotFoundException("No employee found with that id");
		}
		if (teacherInviteRepository.existsBySchoolIdAndTargetEmployeeIdAndUsedFalse(schoolId, employeeId)) {
			throw new IllegalArgumentException("An unused invite already exists for this employee");
		}

		TeacherInvite invite = new TeacherInvite();
		invite.setSchoolId(schoolId);
		invite.setCode(generateCode());
		invite.setExpiresAt(Instant.now().plusSeconds(DEFAULT_VALIDITY_HOURS * 3600));
		invite.setCreatedByEmployeeId(principal.getOwnerId());
		invite.setTargetEmployeeId(employeeId);
		invite.setUsed(false);
		invite = teacherInviteRepository.save(invite);
		return toResponse(invite);
	}

	/**
	 * Lets admin re-view/re-share an invite after the create response - otherwise a lost/never-copied
	 * code would be permanently stuck, since createInviteForEmployee refuses to issue a second one
	 * while an unused invite still exists for that employee.
	 */
	@Transactional(readOnly = true)
	public TeacherInviteResponse getInviteForEmployee(UUID employeeId) {
		UUID schoolId = schoolContext.getSchoolId();
		TeacherInvite invite = teacherInviteRepository
				.findFirstBySchoolIdAndTargetEmployeeIdOrderByCreatedAtDesc(schoolId, employeeId)
				.orElseThrow(() -> new EntityNotFoundException("No invite has been generated for this employee yet"));
		return toResponse(invite);
	}

	private TeacherInviteResponse toResponse(TeacherInvite invite) {
		return new TeacherInviteResponse(
				invite.getCode(), invite.getExpiresAt(), invite.isUsed(), invite.getExpiresAt().isBefore(Instant.now()));
	}

	private String generateCode() {
		SecureRandom random = new SecureRandom();
		StringBuilder code = new StringBuilder(CODE_LENGTH);
		for (int i = 0; i < CODE_LENGTH; i++) {
			code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
		}
		return code.toString();
	}

}
