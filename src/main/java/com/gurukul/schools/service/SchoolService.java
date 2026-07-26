package com.gurukul.schools.service;

import com.gurukul.auth.dto.AuthDtos.LoginResponse;
import com.gurukul.auth.entity.Credential;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.repository.CredentialRepository;
import com.gurukul.auth.security.JwtService;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.entity.EmployeeStatus;
import com.gurukul.employees.entity.EmployeeType;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.schools.dto.SchoolRegistrationRequest;
import com.gurukul.schools.dto.SchoolRegistrationResponse;
import com.gurukul.schools.dto.SchoolResponse;
import com.gurukul.schools.dto.SchoolSearchResponse;
import com.gurukul.schools.dto.SchoolUpdateRequest;
import com.gurukul.schools.entity.School;
import com.gurukul.schools.repository.SchoolRepository;
import com.gurukul.students.repository.ClassSectionRepository;
import com.gurukul.students.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SchoolService {

	private final SchoolRepository schoolRepository;
	private final StudentRepository studentRepository;
	private final ClassSectionRepository classSectionRepository;
	private final EmployeeRepository employeeRepository;
	private final CredentialRepository credentialRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	@Transactional
	public SchoolRegistrationResponse register(SchoolRegistrationRequest request) {
		School school = new School();
		school.setName(request.getName());
		school.setAddress(request.getAddress());
		school.setCity(request.getCity());
		school.setState(request.getState());
		school.setPincode(request.getPincode());
		school.setContactEmail(request.getContactEmail());
		school.setContactPhone(request.getContactPhone());
		school.setPrincipalName(request.getPrincipalName());
		school.setDirectorName(request.getDirectorName());
		School saved = schoolRepository.save(school);

		Employee admin = new Employee();
		admin.setSchoolId(saved.getId());
		admin.setName(request.getPrincipalName());
		admin.setDesignation("Principal");
		admin.setJoinDate(LocalDate.now());
		admin.setStatus(EmployeeStatus.ACTIVE);
		admin.setEmployeeType(EmployeeType.NON_TEACHING);
		admin.setContactPhone(request.getAdminPhone());
		admin = employeeRepository.save(admin);

		Credential credential = new Credential();
		credential.setSchoolId(saved.getId());
		credential.setOwnerType(OwnerType.EMPLOYEE);
		credential.setOwnerId(admin.getId());
		credential.setUsername(request.getAdminUsername() != null && !request.getAdminUsername().isBlank()
				? request.getAdminUsername() : request.getAdminPhone());
		String password = request.getAdminPassword() != null && !request.getAdminPassword().isBlank()
				? request.getAdminPassword() : UUID.randomUUID().toString();
		credential.setPasswordHash(passwordEncoder.encode(password));
		credential.setRole(Role.ADMIN);
		credential = credentialRepository.save(credential);

		String token = jwtService.generateToken(credential);
		LoginResponse adminLogin = new LoginResponse(
				token, "Bearer", credential.getOwnerType(), credential.getOwnerId(),
				credential.getRole(), credential.getSchoolId(), credential.getUsername());

		return new SchoolRegistrationResponse(toResponse(saved), adminLogin);
	}

	public SchoolResponse getById(UUID id) {
		return toResponse(findSchool(id));
	}

	public List<SchoolSearchResponse> list(String name) {
		List<School> schools = (name != null && !name.isBlank())
				? schoolRepository.findAllByNameContainingIgnoreCaseOrderByNameAsc(name.trim())
				: schoolRepository.findAllByOrderByNameAsc();
		return schools.stream().map(SchoolSearchResponse::from).toList();
	}

	@Transactional
	public SchoolResponse update(UUID id, SchoolUpdateRequest request) {
		School school = findSchool(id);
		school.setName(request.getName());
		school.setAddress(request.getAddress());
		school.setCity(request.getCity());
		school.setState(request.getState());
		school.setPincode(request.getPincode());
		school.setContactEmail(request.getContactEmail());
		school.setContactPhone(request.getContactPhone());
		school.setPrincipalName(request.getPrincipalName());
		school.setDirectorName(request.getDirectorName());
		return toResponse(schoolRepository.save(school));
	}

	public void requireExists(UUID id) {
		findSchool(id);
	}

	private SchoolResponse toResponse(School school) {
		UUID schoolId = school.getId();
		long studentCount = studentRepository.countBySchoolId(schoolId);
		long classSectionCount = classSectionRepository.countBySchoolId(schoolId);
		long teacherCount = 0L;
		return SchoolResponse.from(school, studentCount, classSectionCount, teacherCount);
	}

	private School findSchool(UUID id) {
		return schoolRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("School not found"));
	}

}
