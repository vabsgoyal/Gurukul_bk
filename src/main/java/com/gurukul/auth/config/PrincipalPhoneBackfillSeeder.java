package com.gurukul.auth.config;

import com.gurukul.auth.entity.Credential;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.repository.CredentialRepository;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.entity.EmployeeStatus;
import com.gurukul.employees.entity.EmployeeType;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.schools.entity.School;
import com.gurukul.schools.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Runs in EVERY profile including prod - deliberately, per an explicit product decision to keep
 * OTP login live in production despite it still using a hardcoded dummy code ("1234", see
 * OtpService) rather than a real SMS-verified one. Backfills a shared, memorable Principal phone
 * number (9999999999) with an explicit ADMIN credential for any school that doesn't have one yet,
 * so every school - old or new - has a working way to log in as Principal via OTP.
 *
 * Kept separate from DevAdminSeeder on purpose: that one seeds a fixed global password
 * (admin/admin123), a strictly more dangerous exposure than a phone number, and stays dev-only.
 *
 * Known accepted risk: this number is identical across every school and pairs with a dummy OTP
 * that accepts "1234" for ANY phone on file - not just this one. Re-evaluate this seeder (and
 * OtpService's dummy code) once a real SMS provider is integrated.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PrincipalPhoneBackfillSeeder implements ApplicationRunner {

	private static final String PRINCIPAL_PHONE = "9999999999";
	private static final String PRINCIPAL_EMPLOYEE_NAME = "Principal";

	private final SchoolRepository schoolRepository;
	private final EmployeeRepository employeeRepository;
	private final CredentialRepository credentialRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		for (School school : schoolRepository.findAll()) {
			seedIfMissing(school.getId());
		}
	}

	private void seedIfMissing(UUID schoolId) {
		if (credentialRepository.existsBySchoolIdAndUsername(schoolId, PRINCIPAL_PHONE)) {
			return;
		}

		Employee principal = employeeRepository.findAllBySchoolIdOrderByNameAsc(schoolId).stream()
				.filter(e -> PRINCIPAL_EMPLOYEE_NAME.equals(e.getName()))
				.findFirst()
				.orElseGet(() -> {
					Employee employee = new Employee();
					employee.setSchoolId(schoolId);
					employee.setName(PRINCIPAL_EMPLOYEE_NAME);
					employee.setDesignation("Principal");
					employee.setJoinDate(LocalDate.now());
					employee.setStatus(EmployeeStatus.ACTIVE);
					employee.setEmployeeType(EmployeeType.NON_TEACHING);
					employee.setContactPhone(PRINCIPAL_PHONE);
					return employeeRepository.save(employee);
				});

		Credential credential = new Credential();
		credential.setSchoolId(schoolId);
		credential.setOwnerType(OwnerType.EMPLOYEE);
		credential.setOwnerId(principal.getId());
		credential.setUsername(PRINCIPAL_PHONE);
		credential.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
		credential.setRole(Role.ADMIN);
		credentialRepository.save(credential);

		log.warn("Seeded Principal OTP login for school {}: phone={} otp=1234 - accepted-risk backdoor, "
				+ "runs in every profile including prod until real SMS is integrated", schoolId, PRINCIPAL_PHONE);
	}

}
