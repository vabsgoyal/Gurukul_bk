package com.gurukul.auth.service;

import com.gurukul.auth.dto.AuthDtos.GoogleIdTokenRequest;
import com.gurukul.auth.dto.AuthDtos.LoginRequest;
import com.gurukul.auth.dto.AuthDtos.LoginResponse;
import com.gurukul.auth.entity.Credential;
import com.gurukul.auth.google.GoogleTokenVerifier;
import com.gurukul.auth.google.GoogleTokenVerifier.GoogleIdentity;
import com.gurukul.auth.repository.CredentialRepository;
import com.gurukul.auth.security.JwtService;
import com.gurukul.common.SchoolContext;
import com.gurukul.registration.service.RegistrationService;
import com.gurukul.workflow.entity.ApprovalStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final CredentialRepository credentialRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final SchoolContext schoolContext;
	private final GoogleTokenVerifier googleTokenVerifier;
	private final RegistrationService registrationService;

	public LoginResponse login(LoginRequest request) {
		Credential credential = credentialRepository
				.findBySchoolIdAndUsername(schoolContext.getSchoolId(), request.getUsername())
				.orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

		if (!passwordEncoder.matches(request.getPassword(), credential.getPasswordHash())) {
			throw new BadCredentialsException("Invalid username or password");
		}
		requireEnabled(credential);

		return toResponse(credential);
	}

	/** Same enabled/pending-approval gate as password login - a Google-authenticated identity doesn't bypass admin approval. */
	public LoginResponse loginWithGoogle(GoogleIdTokenRequest request) {
		GoogleIdentity identity = googleTokenVerifier.verify(request.getIdToken());
		Credential credential = credentialRepository
				.findBySchoolIdAndGoogleSubject(schoolContext.getSchoolId(), identity.subject())
				.orElseThrow(() -> new BadCredentialsException("No account at this school is linked to this Google account"));

		requireEnabled(credential);

		return toResponse(credential);
	}

	/**
	 * A disabled credential is either still pending (SUBMITTED, or a DRAFT that oddly never got
	 * submitted) or was REJECTED - these need different messages so a rejected applicant isn't told
	 * to just "wait", and doesn't need to guess why login never starts working.
	 */
	private void requireEnabled(Credential credential) {
		if (credential.isEnabled()) {
			return;
		}
		ApprovalStatus status = registrationService.findApprovalStatus(credential.getOwnerType(), credential.getOwnerId());
		if (status == ApprovalStatus.REJECTED) {
			throw new BadCredentialsException("Your registration was rejected - contact your school admin for details");
		}
		throw new BadCredentialsException("Your registration is still pending admin approval");
	}

	private LoginResponse toResponse(Credential credential) {
		String token = jwtService.generateToken(credential);
		return new LoginResponse(
				token,
				"Bearer",
				credential.getOwnerType(),
				credential.getOwnerId(),
				credential.getRole(),
				credential.getSchoolId(),
				credential.getUsername()
		);
	}

}
