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
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Dev/local/test only: backfills two logins for every school that doesn't already have them, so
 * there's always a way to log in without hunting for per-school credentials. Never runs under the
 * "prod" profile - production must provision its first admin credential through a separate,
 * deliberate process (see AdminBackfillService for the real, ops-secret-gated equivalent).
 */
@Component
@Profile("!prod")
@RequiredArgsConstructor
@Slf4j
public class DevAdminSeeder implements ApplicationRunner {

	private static final String DEV_USERNAME = "admin";
	private static final String DEV_PASSWORD = "admin123";

	// Shared, memorable dummy number for OTP login as Principal - dev/test convenience only,
	// never real contact info. Deliberately identical across every school; OTP lookups are
	// already scoped by X-School-Id, so there's no cross-school collision.
	private static final String DEV_PRINCIPAL_PHONE = "9999999999";

	private final SchoolRepository schoolRepository;
	private final EmployeeRepository employeeRepository;
	private final CredentialRepository credentialRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		for (School school : schoolRepository.findAll()) {
			seedAdminCredentialIfMissing(school.getId());
			seedPrincipalPhoneIfMissing(school.getId());
		}
	}

	private void seedAdminCredentialIfMissing(UUID schoolId) {
		if (credentialRepository.existsBySchoolIdAndUsername(schoolId, DEV_USERNAME)) {
			return;
		}

		Employee admin = findEmployeeByName(schoolId, "System Admin")
				.orElseGet(() -> createEmployee(schoolId, "System Admin", "Administrator", null));

		saveCredential(schoolId, admin.getId(), DEV_USERNAME, DEV_PASSWORD);

		log.warn("Seeded DEV-ONLY admin credential for school {}: username={} password={} - never use in production",
				schoolId, DEV_USERNAME, DEV_PASSWORD);
	}

	private void seedPrincipalPhoneIfMissing(UUID schoolId) {
		if (credentialRepository.existsBySchoolIdAndUsername(schoolId, DEV_PRINCIPAL_PHONE)) {
			return;
		}

		Employee principal = findEmployeeByName(schoolId, "Dev Principal")
				.orElseGet(() -> createEmployee(schoolId, "Dev Principal", "Principal", DEV_PRINCIPAL_PHONE));

		saveCredential(schoolId, principal.getId(), DEV_PRINCIPAL_PHONE, UUID.randomUUID().toString());

		log.warn("Seeded DEV-ONLY Principal OTP login for school {}: phone={} otp=1234 - never use in production",
				schoolId, DEV_PRINCIPAL_PHONE);
	}

	private Optional<Employee> findEmployeeByName(UUID schoolId, String name) {
		List<Employee> employees = employeeRepository.findAllBySchoolIdOrderByNameAsc(schoolId);
		return employees.stream().filter(e -> name.equals(e.getName())).findFirst();
	}

	private Employee createEmployee(UUID schoolId, String name, String designation, String contactPhone) {
		Employee employee = new Employee();
		employee.setSchoolId(schoolId);
		employee.setName(name);
		employee.setDesignation(designation);
		employee.setJoinDate(LocalDate.now());
		employee.setStatus(EmployeeStatus.ACTIVE);
		employee.setEmployeeType(EmployeeType.NON_TEACHING);
		employee.setContactPhone(contactPhone);
		return employeeRepository.save(employee);
	}

	private void saveCredential(UUID schoolId, UUID employeeId, String username, String password) {
		Credential credential = new Credential();
		credential.setSchoolId(schoolId);
		credential.setOwnerType(OwnerType.EMPLOYEE);
		credential.setOwnerId(employeeId);
		credential.setUsername(username);
		credential.setPasswordHash(passwordEncoder.encode(password));
		credential.setRole(Role.ADMIN);
		credentialRepository.save(credential);
	}

}
