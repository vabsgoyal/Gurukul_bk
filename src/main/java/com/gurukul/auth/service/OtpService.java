package com.gurukul.auth.service;

import com.gurukul.auth.dto.AuthDtos.LoginResponse;
import com.gurukul.auth.entity.Credential;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.repository.CredentialRepository;
import com.gurukul.auth.security.JwtService;
import com.gurukul.common.SchoolContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Dummy OTP for now: always "1234", no real code generation/expiry/SMS delivery. Swap in a
 * real provider behind {@link #requestOtp} / the otp check in {@link #verifyOtp} when ready
 * (or enable app.auth.supabase.enabled - see SupabaseAuthController - for real phone-OTP login
 * via Supabase, which this service is intentionally left untouched by).
 */
@Service
@RequiredArgsConstructor
public class OtpService {

	private static final String DUMMY_OTP = "1234";

	private final PhoneOwnerResolver phoneOwnerResolver;
	private final CredentialRepository credentialRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final SchoolContext schoolContext;

	public void requestOtp(String phone) {
		phoneOwnerResolver.resolve(schoolContext.getSchoolId(), phone);
	}

	@Transactional
	public LoginResponse verifyOtp(String phone, String otp) {
		if (!DUMMY_OTP.equals(otp)) {
			throw new BadCredentialsException("Invalid OTP");
		}

		PhoneOwnerResolver.PhoneOwner owner = phoneOwnerResolver.resolve(schoolContext.getSchoolId(), phone);
		Credential credential = credentialRepository.findByOwnerTypeAndOwnerId(owner.ownerType(), owner.ownerId())
				.orElseGet(() -> createCredentialFor(owner, phone));

		String token = jwtService.generateToken(credential);
		return new LoginResponse(
				token, "Bearer", credential.getOwnerType(), credential.getOwnerId(),
				credential.getRole(), credential.getSchoolId(), credential.getUsername());
	}

	private Credential createCredentialFor(PhoneOwnerResolver.PhoneOwner owner, String phone) {
		Credential credential = new Credential();
		credential.setSchoolId(schoolContext.getSchoolId());
		credential.setOwnerType(owner.ownerType());
		credential.setOwnerId(owner.ownerId());
		credential.setUsername(phone);
		credential.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
		credential.setRole(owner.ownerType() == OwnerType.EMPLOYEE ? Role.TEACHER : Role.STUDENT);
		return credentialRepository.save(credential);
	}

}
