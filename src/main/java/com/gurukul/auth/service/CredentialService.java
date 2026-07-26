package com.gurukul.auth.service;

import com.gurukul.auth.dto.AuthDtos.CredentialRequest;
import com.gurukul.auth.dto.AuthDtos.CredentialResponse;
import com.gurukul.auth.entity.Credential;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.repository.CredentialRepository;
import com.gurukul.common.SchoolContext;
import com.gurukul.employees.service.EmployeeService;
import com.gurukul.students.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CredentialService {

	private final CredentialRepository credentialRepository;
	private final EmployeeService employeeService;
	private final StudentService studentService;
	private final PasswordEncoder passwordEncoder;
	private final SchoolContext schoolContext;

	@Transactional
	public CredentialResponse createForEmployee(UUID employeeId, CredentialRequest request) {
		var employee = employeeService.getScopedEntity(employeeId);
		return create(OwnerType.EMPLOYEE, employee.getId(), request);
	}

	@Transactional
	public CredentialResponse createForStudent(UUID studentId, CredentialRequest request) {
		var student = studentService.getScopedEntity(studentId);
		return create(OwnerType.STUDENT, student.getId(), request);
	}

	private CredentialResponse create(OwnerType ownerType, UUID ownerId, CredentialRequest request) {
		UUID schoolId = schoolContext.getSchoolId();
		if (credentialRepository.existsBySchoolIdAndUsername(schoolId, request.getUsername())) {
			throw new IllegalArgumentException("Username already taken for this school");
		}
		if (credentialRepository.existsByOwnerTypeAndOwnerId(ownerType, ownerId)) {
			throw new IllegalArgumentException("This owner already has a credential");
		}

		Credential credential = new Credential();
		credential.setSchoolId(schoolId);
		credential.setOwnerType(ownerType);
		credential.setOwnerId(ownerId);
		credential.setUsername(request.getUsername());
		credential.setPasswordHash(passwordEncoder.encode(request.getPassword()));
		credential.setRole(request.getRole());

		return CredentialResponse.from(credentialRepository.save(credential));
	}

}
