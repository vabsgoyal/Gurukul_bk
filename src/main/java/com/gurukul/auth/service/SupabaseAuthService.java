package com.gurukul.auth.service;

import com.gurukul.auth.dto.AuthDtos.LoginResponse;
import com.gurukul.auth.entity.Credential;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.repository.CredentialRepository;
import com.gurukul.auth.security.JwtService;
import com.gurukul.common.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Resolves a Supabase-verified phone-OTP session into this app's own login (same LoginResponse
 * shape/JWT the legacy dummy-OTP path issues), auto-linking (or auto-creating, mirroring
 * OtpService's existing first-login behavior) a Credential the first time a given Supabase user
 * authenticates. Never auto-creates ADMIN.
 */
@Service
@RequiredArgsConstructor
public class SupabaseAuthService {

	private final CredentialRepository credentialRepository;
	private final PhoneOwnerResolver phoneOwnerResolver;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	@Value("${app.auth.supabase.phone-country-code:91}")
	private String phoneCountryCode;

	@Transactional
	public LoginResponse login(UUID supabaseUserId, String supabasePhoneClaim, UUID schoolId) {
		Credential credential = credentialRepository.findBySupabaseUserId(supabaseUserId)
				.orElseGet(() -> linkOrCreate(supabaseUserId, supabasePhoneClaim, schoolId));

		String token = jwtService.generateToken(credential);
		return new LoginResponse(
				token, "Bearer", credential.getOwnerType(), credential.getOwnerId(),
				credential.getRole(), credential.getSchoolId(), credential.getUsername());
	}

	private Credential linkOrCreate(UUID supabaseUserId, String supabasePhoneClaim, UUID schoolId) {
		ResolvedPhone resolved = resolveOwnerTryingBothPhoneFormats(schoolId, supabasePhoneClaim);

		Credential credential = credentialRepository
				.findByOwnerTypeAndOwnerId(resolved.owner().ownerType(), resolved.owner().ownerId())
				.orElseGet(() -> {
					Credential created = new Credential();
					created.setSchoolId(schoolId);
					created.setOwnerType(resolved.owner().ownerType());
					created.setOwnerId(resolved.owner().ownerId());
					created.setUsername(resolved.matchedPhone());
					created.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
					created.setRole(resolved.owner().ownerType() == OwnerType.EMPLOYEE ? Role.TEACHER : Role.STUDENT);
					return created;
				});
		credential.setSupabaseUserId(supabaseUserId);
		return credentialRepository.save(credential);
	}

	private record ResolvedPhone(PhoneOwnerResolver.PhoneOwner owner, String matchedPhone) {
	}

	// Supabase's phone claim is E.164-without-"+" (country code + local number, e.g.
	// "919876543210"); Employee.contactPhone/Student.parentContact are bare local numbers
	// entered by school staff (e.g. "9876543210"). Try the claim verbatim first (covers any
	// school that *did* enter numbers with a country code), then with the configured country
	// code prefix stripped, before giving up.
	private ResolvedPhone resolveOwnerTryingBothPhoneFormats(UUID schoolId, String supabasePhoneClaim) {
		try {
			return new ResolvedPhone(phoneOwnerResolver.resolve(schoolId, supabasePhoneClaim), supabasePhoneClaim);
		} catch (EntityNotFoundException firstAttemptFailed) {
			if (supabasePhoneClaim.startsWith(phoneCountryCode) && supabasePhoneClaim.length() > phoneCountryCode.length()) {
				String localNumber = supabasePhoneClaim.substring(phoneCountryCode.length());
				return new ResolvedPhone(phoneOwnerResolver.resolve(schoolId, localNumber), localNumber);
			}
			throw firstAttemptFailed;
		}
	}

}
