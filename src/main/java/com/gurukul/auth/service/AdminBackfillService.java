package com.gurukul.auth.service;

import com.gurukul.auth.dto.AdminBackfillDtos.AdminBackfillResponse;
import com.gurukul.auth.dto.AdminBackfillDtos.BackfilledAdmin;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ops-only, gated by a shared secret (not user auth) rather than a role check, since a school
 * with no existing ADMIN credential has nobody who could authenticate to call an admin-gated
 * endpoint in the first place. Runs in every profile including prod - unlike DevAdminSeeder,
 * which is deliberately excluded from prod and uses a fixed dev password.
 */
@Service
public class AdminBackfillService {

	private final SchoolRepository schoolRepository;
	private final CredentialRepository credentialRepository;
	private final EmployeeRepository employeeRepository;
	private final PasswordEncoder passwordEncoder;
	private final String opsBackfillKey;

	public AdminBackfillService(
			SchoolRepository schoolRepository,
			CredentialRepository credentialRepository,
			EmployeeRepository employeeRepository,
			PasswordEncoder passwordEncoder,
			@Value("${app.ops.backfill-key}") String opsBackfillKey) {
		this.schoolRepository = schoolRepository;
		this.credentialRepository = credentialRepository;
		this.employeeRepository = employeeRepository;
		this.passwordEncoder = passwordEncoder;
		this.opsBackfillKey = opsBackfillKey;
	}

	@Transactional
	public AdminBackfillResponse backfill(String providedKey) {
		if (providedKey == null || !opsBackfillKey.equals(providedKey)) {
			throw new BadCredentialsException("Invalid ops key");
		}

		List<BackfilledAdmin> created = new ArrayList<>();
		for (School school : schoolRepository.findAll()) {
			if (credentialRepository.existsBySchoolIdAndRole(school.getId(), Role.ADMIN)) {
				continue;
			}
			created.add(createAdminFor(school));
		}
		return new AdminBackfillResponse(created.size(), created);
	}

	private BackfilledAdmin createAdminFor(School school) {
		Employee employee = new Employee();
		employee.setSchoolId(school.getId());
		employee.setName("System Admin");
		employee.setDesignation("Administrator");
		employee.setJoinDate(LocalDate.now());
		employee.setStatus(EmployeeStatus.ACTIVE);
		employee.setEmployeeType(EmployeeType.NON_TEACHING);
		employee = employeeRepository.save(employee);

		String username = "admin-" + school.getId().toString().substring(0, 8);
		String password = UUID.randomUUID().toString();

		Credential credential = new Credential();
		credential.setSchoolId(school.getId());
		credential.setOwnerType(OwnerType.EMPLOYEE);
		credential.setOwnerId(employee.getId());
		credential.setUsername(username);
		credential.setPasswordHash(passwordEncoder.encode(password));
		credential.setRole(Role.ADMIN);
		credentialRepository.save(credential);

		return new BackfilledAdmin(school.getId(), school.getName(), username, password);
	}

}
