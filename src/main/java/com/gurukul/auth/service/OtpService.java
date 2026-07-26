package com.gurukul.auth.service;

import com.gurukul.auth.dto.AuthDtos.LoginResponse;
import com.gurukul.auth.entity.Credential;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.repository.CredentialRepository;
import com.gurukul.auth.security.JwtService;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.students.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Dummy OTP for now: always "1234", no real code generation/expiry/SMS delivery. Swap in a
 * real provider behind {@link #requestOtp} / the otp check in {@link #verifyOtp} when ready.
 */
@Service
@RequiredArgsConstructor
public class OtpService {

	private static final String DUMMY_OTP = "1234";

	private final EmployeeRepository employeeRepository;
	private final StudentRepository studentRepository;
	private final CredentialRepository credentialRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final SchoolContext schoolContext;

	public void requestOtp(String phone) {
		resolveOwner(phone);
	}

	@Transactional
	public LoginResponse verifyOtp(String phone, String otp) {
		if (!DUMMY_OTP.equals(otp)) {
			throw new BadCredentialsException("Invalid OTP");
		}

		PhoneOwner owner = resolveOwner(phone);
		Credential credential = credentialRepository.findByOwnerTypeAndOwnerId(owner.ownerType(), owner.ownerId())
				.orElseGet(() -> createCredentialFor(owner, phone));

		String token = jwtService.generateToken(credential);
		return new LoginResponse(
				token, "Bearer", credential.getOwnerType(), credential.getOwnerId(),
				credential.getRole(), credential.getSchoolId(), credential.getUsername());
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
