package com.gurukul.auth.service;

import com.gurukul.auth.dto.AuthDtos.LoginRequest;
import com.gurukul.auth.dto.AuthDtos.LoginResponse;
import com.gurukul.auth.entity.Credential;
import com.gurukul.auth.repository.CredentialRepository;
import com.gurukul.auth.security.JwtService;
import com.gurukul.common.SchoolContext;
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

	public LoginResponse login(LoginRequest request) {
		Credential credential = credentialRepository
				.findBySchoolIdAndUsername(schoolContext.getSchoolId(), request.getUsername())
				.orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

		if (!passwordEncoder.matches(request.getPassword(), credential.getPasswordHash())) {
			throw new BadCredentialsException("Invalid username or password");
		}

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
