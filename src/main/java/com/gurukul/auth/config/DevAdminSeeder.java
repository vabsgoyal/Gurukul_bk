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
import java.util.UUID;

/**
 * Dev/local/test only: backfills one ADMIN credential (username=admin, password=admin123) for
 * every school that doesn't already have one, so there's always a way to log in and provision
 * further credentials. Never runs under the "prod" profile - production must provision its first
 * admin credential through a separate, deliberate process.
 */
@Component
@Profile("!prod")
@RequiredArgsConstructor
@Slf4j
public class DevAdminSeeder implements ApplicationRunner {

	private static final String DEV_USERNAME = "admin";
	private static final String DEV_PASSWORD = "admin123";

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
		if (credentialRepository.existsBySchoolIdAndUsername(schoolId, DEV_USERNAME)) {
			return;
		}

		Employee admin = employeeRepository.findAllBySchoolIdOrderByNameAsc(schoolId).stream()
				.filter(e -> "System Admin".equals(e.getName()))
				.findFirst()
				.orElseGet(() -> {
					Employee employee = new Employee();
					employee.setSchoolId(schoolId);
					employee.setName("System Admin");
					employee.setDesignation("Administrator");
					employee.setJoinDate(LocalDate.now());
					employee.setStatus(EmployeeStatus.ACTIVE);
					employee.setEmployeeType(EmployeeType.NON_TEACHING);
					return employeeRepository.save(employee);
				});

		Credential credential = new Credential();
		credential.setSchoolId(schoolId);
		credential.setOwnerType(OwnerType.EMPLOYEE);
		credential.setOwnerId(admin.getId());
		credential.setUsername(DEV_USERNAME);
		credential.setPasswordHash(passwordEncoder.encode(DEV_PASSWORD));
		credential.setRole(Role.ADMIN);
		credentialRepository.save(credential);

		log.warn("Seeded DEV-ONLY admin credential for school {}: username={} password={} - never use in production",
				schoolId, DEV_USERNAME, DEV_PASSWORD);
	}

}
