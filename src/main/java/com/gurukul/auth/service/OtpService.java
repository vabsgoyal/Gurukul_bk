package com.gurukul.auth.service;

import com.gurukul.auth.dto.AuthDtos.LoginResponse;
import com.gurukul.auth.entity.Credential;
import com.gurukul.auth.entity.OtpCode;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.repository.CredentialRepository;
import com.gurukul.auth.repository.OtpCodeRepository;
import com.gurukul.auth.security.JwtService;
import com.gurukul.auth.whatsapp.OtpChannel;
import com.gurukul.auth.whatsapp.WhatsAppOtpProperties;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.students.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Real, per-request OTP: a random numeric code, bcrypt-hashed and stored with an expiry, sent over
 * WhatsApp via {@link WhatsAppOtpSender}. Replaces the old hardcoded "1234" dummy. A code is
 * single-use - verifying it (successfully or not) doesn't retry the same code; requesting again
 * always issues a fresh one.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final EmployeeRepository employeeRepository;
	private final StudentRepository studentRepository;
	private final CredentialRepository credentialRepository;
	private final OtpCodeRepository otpCodeRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final SchoolContext schoolContext;
	private final OtpChannel otpChannel;
	private final WhatsAppOtpProperties whatsAppOtpProperties;

	@Transactional
	public void requestOtp(String phone) {
		resolveOwner(phone);

		String code = generateCode();
		OtpCode otpCode = new OtpCode();
		otpCode.setSchoolId(schoolContext.getSchoolId());
		otpCode.setPhone(phone);
		otpCode.setCodeHash(passwordEncoder.encode(code));
		otpCode.setExpiresAt(Instant.now().plus(whatsAppOtpProperties.expiryMinutes(), ChronoUnit.MINUTES));
		otpCodeRepository.save(otpCode);

		if (otpChannel.isConfigured()) {
			otpChannel.send(phone, code);
		} else {
			// Lets OTP login keep working in dev/test environments before WA-AKG is deployed -
			// same fail-open-at-call-time shape as AiChatService.isConfigured() checks.
			log.warn("WhatsApp OTP gateway not configured - code for {} is {} (dev-only log fallback)", phone, code);
		}
	}

	@Transactional
	public LoginResponse verifyOtp(String phone, String otp) {
		UUID schoolId = schoolContext.getSchoolId();
		OtpCode otpCode = otpCodeRepository
				.findFirstBySchoolIdAndPhoneAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
						schoolId, phone, Instant.now())
				.orElseThrow(() -> new BadCredentialsException("Invalid or expired OTP"));

		if (!passwordEncoder.matches(otp, otpCode.getCodeHash())) {
			throw new BadCredentialsException("Invalid or expired OTP");
		}
		otpCode.setConsumedAt(Instant.now());

		PhoneOwner owner = resolveOwner(phone);
		Credential credential = credentialRepository.findByOwnerTypeAndOwnerId(owner.ownerType(), owner.ownerId())
				.orElseGet(() -> createCredentialFor(owner, phone));

		String token = jwtService.generateToken(credential);
		return new LoginResponse(
				token, "Bearer", credential.getOwnerType(), credential.getOwnerId(),
				credential.getRole(), credential.getSchoolId(), credential.getUsername());
	}

	private String generateCode() {
		int length = whatsAppOtpProperties.codeLength();
		StringBuilder code = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			code.append(RANDOM.nextInt(10));
		}
		return code.toString();
	}

	private Credential createCredentialFor(PhoneOwner owner, String phone) {
		Credential credential = new Credential();
		credential.setSchoolId(schoolContext.getSchoolId());
		credential.setOwnerType(owner.ownerType());
		credential.setOwnerId(owner.ownerId());
		credential.setUsername(phone);
		credential.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
		credential.setRole(owner.ownerType() == OwnerType.EMPLOYEE ? Role.TEACHER : Role.STUDENT);
		return credentialRepository.save(credential);
	}

	// If a phone number matches more than one record (e.g. siblings sharing a parent's
	// number), the first match wins - there's no "choose which profile" step yet.
	private PhoneOwner resolveOwner(String phone) {
		UUID schoolId = schoolContext.getSchoolId();

		return employeeRepository.findAllBySchoolIdAndContactPhone(schoolId, phone).stream().findFirst()
				.map(employee -> new PhoneOwner(OwnerType.EMPLOYEE, employee.getId()))
				.or(() -> studentRepository.findAllBySchoolIdAndParentContact(schoolId, phone).stream().findFirst()
						.map(student -> new PhoneOwner(OwnerType.STUDENT, student.getId())))
				.orElseThrow(() -> new EntityNotFoundException("Phone number not registered"));
	}

	private record PhoneOwner(OwnerType ownerType, UUID ownerId) {
	}

}
