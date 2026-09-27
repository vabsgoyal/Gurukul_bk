package com.gurukul.auth.service;

import com.gurukul.auth.dto.AuthDtos.LoginResponse;
import com.gurukul.auth.dto.OtpDtos.LoginProfile;
import com.gurukul.auth.dto.OtpDtos.OtpVerifyResponse;
import com.gurukul.auth.entity.Credential;
import com.gurukul.auth.entity.OtpCode;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.repository.CredentialRepository;
import com.gurukul.auth.repository.OtpCodeRepository;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.auth.security.JwtService;
import com.gurukul.auth.whatsapp.OtpChannel;
import com.gurukul.auth.whatsapp.WhatsAppOtpProperties;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.students.entity.ClassSection;
import com.gurukul.students.entity.Student;
import com.gurukul.students.repository.StudentRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
	private static final String ACCOUNT_DISABLED = "This account has been disabled - please contact your school";

	private final EmployeeRepository employeeRepository;
	private final StudentRepository studentRepository;
	private final CredentialRepository credentialRepository;
	private final OtpCodeRepository otpCodeRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final SessionTokenService sessionTokenService;
	private final SchoolContext schoolContext;
	private final OtpChannel otpChannel;
	private final WhatsAppOtpProperties whatsAppOtpProperties;

	@Transactional
	public void requestOtp(String phone) {
		profilesFor(schoolContext.getSchoolId(), phone);

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

	/**
	 * One profile on the phone: logs straight in, as before. Several (siblings sharing a parent's
	 * number, or a teacher who is also a parent): also returns the list plus a selection token, and
	 * the client finishes with {@link #selectProfile}.
	 */
	@Transactional
	public OtpVerifyResponse verifyOtp(String phone, String otp) {
		UUID schoolId = schoolContext.getSchoolId();
		OtpCode otpCode = otpCodeRepository
				.findFirstBySchoolIdAndPhoneAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
						schoolId, phone, Instant.now())
				.orElseThrow(() -> new BadCredentialsException("Invalid or expired OTP"));

		if (!passwordEncoder.matches(otp, otpCode.getCodeHash())) {
			throw new BadCredentialsException("Invalid or expired OTP");
		}
		otpCode.setConsumedAt(Instant.now());

		List<PhoneProfile> profiles = profilesFor(schoolId, phone);
		if (profiles.size() == 1) {
			return OtpVerifyResponse.of(login(schoolId, profiles.getFirst(), phone), null, List.of());
		}
		// Also log in as the first profile, so app versions without the picker still work - but with an
		// access token only: the profile actually picked gets its full session from select-profile.
		LoginResponse login = sessionTokenService.issueAccessOnly(credentialFor(schoolId, profiles.getFirst(), phone));
		return OtpVerifyResponse.of(login,
				jwtService.generateProfileSelectionToken(schoolId, phone), toLoginProfiles(profiles, null));
	}

	@Transactional
	public LoginResponse selectProfile(String selectionToken, OwnerType ownerType, UUID ownerId) {
		String phone;
		try {
			phone = jwtService.parseProfileSelectionToken(selectionToken, schoolContext.getSchoolId());
		} catch (JwtException | IllegalArgumentException ex) {
			throw new BadCredentialsException("Profile selection expired - request a new OTP");
		}
		UUID schoolId = schoolContext.getSchoolId();
		return login(schoolId, findProfile(profilesFor(schoolId, phone), ownerType, ownerId), phone);
	}

	/**
	 * Profiles the caller can switch to without a new OTP. A student login only sees its siblings,
	 * never a staff profile on the same phone: a child's own password login must not reach a
	 * parent's TEACHER/ADMIN account. A staff login sees everything on its phone.
	 */
	@Transactional(readOnly = true)
	public List<LoginProfile> switchableProfiles(AuthPrincipal principal) {
		return toLoginProfiles(switchableFor(principal), principal);
	}

	/** Pass the current refresh token to end the old profile's session along with the switch. */
	@Transactional
	public LoginResponse switchProfile(AuthPrincipal principal, OwnerType ownerType, UUID ownerId, String refreshToken) {
		String phone = phoneOf(principal);
		LoginResponse login = login(principal.getSchoolId(), findProfile(switchableFor(principal), ownerType, ownerId), phone);
		sessionTokenService.revoke(refreshToken);
		return login;
	}

	private List<PhoneProfile> switchableFor(AuthPrincipal principal) {
		String phone = phoneOf(principal);
		if (phone == null || phone.isBlank()) {
			return List.of();
		}
		List<PhoneProfile> profiles = profilesFor(principal.getSchoolId(), phone);
		if (principal.getOwnerType() == OwnerType.STUDENT) {
			return profiles.stream().filter(profile -> profile.ownerType() == OwnerType.STUDENT).toList();
		}
		return profiles;
	}

	private String phoneOf(AuthPrincipal principal) {
		UUID schoolId = principal.getSchoolId();
		return switch (principal.getOwnerType()) {
			case EMPLOYEE -> employeeRepository.findByIdAndSchoolId(principal.getOwnerId(), schoolId)
					.map(Employee::getContactPhone).orElse(null);
			case STUDENT -> studentRepository.findByIdAndSchoolId(principal.getOwnerId(), schoolId)
					.map(Student::getParentContact).orElse(null);
			// Self-registered parent accounts reach their children through parent_student_link, not a shared phone.
			case PARENT -> null;
		};
	}

	private static PhoneProfile findProfile(List<PhoneProfile> profiles, OwnerType ownerType, UUID ownerId) {
		return profiles.stream()
				.filter(profile -> profile.ownerType() == ownerType && profile.ownerId().equals(ownerId))
				.findFirst()
				.orElseThrow(() -> new AccessDeniedException("That profile isn't linked to this phone number"));
	}

	private LoginResponse login(UUID schoolId, PhoneProfile profile, String phone) {
		return sessionTokenService.issue(credentialFor(schoolId, profile, phone));
	}

	/** Password and Google login refuse a disabled credential; OTP must too. */
	private Credential credentialFor(UUID schoolId, PhoneProfile profile, String phone) {
		Credential credential = credentialRepository.findByOwnerTypeAndOwnerId(profile.ownerType(), profile.ownerId())
				.orElseGet(() -> createCredentialFor(schoolId, profile, phone));
		if (!credential.isEnabled()) {
			throw new BadCredentialsException(ACCOUNT_DISABLED);
		}
		return credential;
	}

	private boolean isDisabled(OwnerType ownerType, UUID ownerId) {
		return credentialRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId)
				.map(credential -> !credential.isEnabled()).orElse(false);
	}

	private List<LoginProfile> toLoginProfiles(List<PhoneProfile> profiles, AuthPrincipal principal) {
		return profiles.stream().map(profile -> {
			boolean current = principal != null
					&& principal.getOwnerType() == profile.ownerType() && principal.getOwnerId().equals(profile.ownerId());
			if (profile.employee() != null) {
				Role role = credentialRepository.findByOwnerTypeAndOwnerId(OwnerType.EMPLOYEE, profile.ownerId())
						.map(Credential::getRole).orElse(Role.TEACHER);
				return new LoginProfile(OwnerType.EMPLOYEE, profile.ownerId(), profile.employee().getName(), role,
						null, null, null, null, current);
			}
			Student student = profile.student();
			ClassSection section = student.getClassSection();
			return new LoginProfile(OwnerType.STUDENT, profile.ownerId(), student.getName(), Role.STUDENT,
					section.getClassName(), section.getSection(), student.getRollNumber(), student.getStatus().name(),
					current);
		}).toList();
	}

	private String generateCode() {
		int length = whatsAppOtpProperties.codeLength();
		StringBuilder code = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			code.append(RANDOM.nextInt(10));
		}
		return code.toString();
	}

	// Siblings share one phone but each gets their own credential, and usernames are unique per
	// school - so only the first profile gets the bare phone as its username.
	private Credential createCredentialFor(UUID schoolId, PhoneProfile profile, String phone) {
		String username = credentialRepository.existsBySchoolIdAndUsername(schoolId, phone)
				? phone + "-" + profile.ownerId().toString().substring(0, 8)
				: phone;

		Credential credential = new Credential();
		credential.setSchoolId(schoolId);
		credential.setOwnerType(profile.ownerType());
		credential.setOwnerId(profile.ownerId());
		credential.setUsername(username);
		credential.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
		credential.setRole(profile.ownerType() == OwnerType.EMPLOYEE ? Role.TEACHER : Role.STUDENT);
		return credentialRepository.save(credential);
	}

	/**
	 * Staff first, then students by name - a stable order, so the picker never reshuffles. Profiles
	 * whose login has been disabled are left out, so they can't be picked or switched to.
	 */
	private List<PhoneProfile> profilesFor(UUID schoolId, String phone) {
		List<PhoneProfile> profiles = new ArrayList<>();
		employeeRepository.findAllBySchoolIdAndContactPhone(schoolId, phone).stream()
				.sorted(Comparator.comparing(Employee::getName).thenComparing(Employee::getId))
				.forEach(employee -> profiles.add(new PhoneProfile(OwnerType.EMPLOYEE, employee.getId(), employee, null)));
		studentRepository.findAllBySchoolIdAndParentContact(schoolId, phone).stream()
				.sorted(Comparator.comparing(Student::getName).thenComparing(Student::getId))
				.forEach(student -> profiles.add(new PhoneProfile(OwnerType.STUDENT, student.getId(), null, student)));
		if (profiles.isEmpty()) {
			throw new EntityNotFoundException("Phone number not registered");
		}
		List<PhoneProfile> enabled = profiles.stream().filter(p -> !isDisabled(p.ownerType(), p.ownerId())).toList();
		if (enabled.isEmpty()) {
			throw new BadCredentialsException(ACCOUNT_DISABLED);
		}
		return enabled;
	}

	private record PhoneProfile(OwnerType ownerType, UUID ownerId, Employee employee, Student student) {
	}

}
