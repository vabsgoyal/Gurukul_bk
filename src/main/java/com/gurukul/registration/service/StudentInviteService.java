package com.gurukul.registration.service;

import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.registration.dto.RegistrationDtos.StudentInviteResponse;
import com.gurukul.registration.entity.StudentInvite;
import com.gurukul.registration.repository.StudentInviteRepository;
import com.gurukul.students.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentInviteService {

	private static final int CODE_LENGTH = 8;
	private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final long DEFAULT_VALIDITY_HOURS = 72;

	private final StudentInviteRepository studentInviteRepository;
	private final StudentRepository studentRepository;
	private final SchoolContext schoolContext;

	/**
	 * Generates an invite tied to a specific, already-admitted Student - the registering student
	 * (or their parent) just claims that record. Idempotent, same as TeacherInviteService: calling
	 * this again for the same student while a prior invite is still unused and unexpired just
	 * re-returns that same code, rather than erroring. An unused-but-expired invite is silently
	 * replaced with a fresh one.
	 */
	@Transactional
	public StudentInviteResponse createInviteForStudent(AuthPrincipal principal, UUID studentId) {
		UUID schoolId = schoolContext.getSchoolId();
		if (studentRepository.findByIdAndSchoolId(studentId, schoolId).isEmpty()) {
			throw new EntityNotFoundException("No student found with that id");
		}

		Optional<StudentInvite> existing = studentInviteRepository.findBySchoolIdAndTargetStudentIdAndUsedFalse(schoolId, studentId);
		if (existing.isPresent() && existing.get().getExpiresAt().isAfter(Instant.now())) {
			StudentInvite invite = existing.get();
			return new StudentInviteResponse(invite.getCode(), invite.getExpiresAt());
		}
		existing.ifPresent(studentInviteRepository::delete);

		StudentInvite invite = new StudentInvite();
		invite.setSchoolId(schoolId);
		invite.setCode(generateCode());
		invite.setExpiresAt(Instant.now().plusSeconds(DEFAULT_VALIDITY_HOURS * 3600));
		invite.setCreatedByEmployeeId(principal.getOwnerId());
		invite.setTargetStudentId(studentId);
		invite.setUsed(false);
		invite = studentInviteRepository.save(invite);
		return new StudentInviteResponse(invite.getCode(), invite.getExpiresAt());
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
