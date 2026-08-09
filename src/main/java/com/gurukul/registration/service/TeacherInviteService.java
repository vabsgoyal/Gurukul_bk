package com.gurukul.registration.service;

import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.SchoolContext;
import com.gurukul.registration.dto.RegistrationDtos.TeacherInviteResponse;
import com.gurukul.registration.entity.TeacherInvite;
import com.gurukul.registration.repository.TeacherInviteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TeacherInviteService {

	private static final int CODE_LENGTH = 8;
	private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final long DEFAULT_VALIDITY_HOURS = 72;

	private final TeacherInviteRepository teacherInviteRepository;
	private final SchoolContext schoolContext;

	@Transactional
	public TeacherInviteResponse createInvite(AuthPrincipal principal) {
		TeacherInvite invite = new TeacherInvite();
		invite.setSchoolId(schoolContext.getSchoolId());
		invite.setCode(generateCode());
		invite.setExpiresAt(Instant.now().plusSeconds(DEFAULT_VALIDITY_HOURS * 3600));
		invite.setCreatedByEmployeeId(principal.getOwnerId());
		invite.setUsed(false);
		invite = teacherInviteRepository.save(invite);
		return new TeacherInviteResponse(invite.getCode(), invite.getExpiresAt());
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
