package com.gurukul.employees.service;

import com.gurukul.auth.entity.Credential;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.repository.CredentialRepository;
import com.gurukul.auth.security.AuthContext;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.FuzzyMatcher;
import com.gurukul.common.PageResponse;
import com.gurukul.common.PartialUpdate;
import com.gurukul.common.SchoolContext;
import com.gurukul.employees.dto.EmployeeRequest;
import com.gurukul.employees.dto.EmployeeResponse;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.entity.EmployeeStatus;
import com.gurukul.employees.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeService {

	private static final int SEARCH_RESULT_LIMIT = 50;

	private final EmployeeRepository employeeRepository;
	private final CredentialRepository credentialRepository;
	private final SchoolContext schoolContext;

	public PageResponse<EmployeeResponse> list(int page, int size) {
		UUID schoolId = schoolContext.getSchoolId();
		Map<UUID, Role> roles = rolesByEmployeeId(schoolId);
		Slice<Employee> result = employeeRepository.findAllBySchoolIdOrderByNameAsc(schoolId, PageRequest.of(page, size));
		Long totalElements = page == 0 ? employeeRepository.countBySchoolId(schoolId) : null;
		return new PageResponse<>(
				result.getContent().stream().map(e -> EmployeeResponse.from(e, roles.get(e.getId()))).toList(),
				result.hasNext(),
				totalElements);
	}

	public List<EmployeeResponse> search(String query) {
		if (query == null || query.isBlank()) {
			throw new IllegalArgumentException("Search query must not be blank");
		}
		UUID schoolId = schoolContext.getSchoolId();
		Map<UUID, Role> roles = rolesByEmployeeId(schoolId);
		return employeeRepository.findAllBySchoolIdOrderByNameAsc(schoolId).stream()
				.filter(e -> FuzzyMatcher.anyFieldMatches(query, e.getName()))
				.sorted(Comparator.comparingDouble((Employee e) -> FuzzyMatcher.bestScore(query, e.getName())).reversed())
				.limit(SEARCH_RESULT_LIMIT)
				.map(e -> EmployeeResponse.from(e, roles.get(e.getId())))
				.toList();
	}

	public EmployeeResponse getById(UUID id) {
		Employee employee = findScoped(id);
		return EmployeeResponse.from(employee, roleOf(id));
	}

	private Role roleOf(UUID employeeId) {
		return credentialRepository.findByOwnerTypeAndOwnerId(OwnerType.EMPLOYEE, employeeId)
				.map(Credential::getRole)
				.orElse(null);
	}

	private Map<UUID, Role> rolesByEmployeeId(UUID schoolId) {
		return credentialRepository.findAllBySchoolIdAndOwnerType(schoolId, OwnerType.EMPLOYEE).stream()
				.collect(Collectors.toMap(Credential::getOwnerId, Credential::getRole, (a, b) -> a));
	}

	@Transactional
	public EmployeeResponse create(EmployeeRequest request) {
		// OTP login finds staff by phone and signs in as that record, so a phone on a new staff
		// record is a new login. Teachers can still add a colleague by name (EmployeePicker).
		if (!isAdmin() && request.getContactPhone() != null && !request.getContactPhone().isBlank()) {
			throw new AccessDeniedException("Only an admin can set a staff member's phone number");
		}
		return EmployeeResponse.from(createEntity(request), null);
	}

	/** Returns the entity (not just its response DTO) - used by RegistrationService, which needs the id for a Credential. */
	@Transactional
	public Employee createEntity(EmployeeRequest request) {
		Employee employee = new Employee();
		employee.setSchoolId(schoolContext.getSchoolId());
		applyRequest(employee, request);
		employee.setStatus(request.getStatus() != null ? request.getStatus() : EmployeeStatus.ACTIVE);
		return employeeRepository.save(employee);
	}

	@Transactional
	public EmployeeResponse update(UUID id, EmployeeRequest request) {
		// Changing someone else's phone would let the new number log in as them by OTP.
		if (!isAdmin() && !isSelf(id)) {
			throw new AccessDeniedException("You can only edit your own staff record");
		}
		Employee employee = findScoped(id);
		applyRequest(employee, request);
		if (request.getStatus() != null) {
			employee.setStatus(request.getStatus());
		}
		Employee saved = employeeRepository.save(employee);
		Role role = credentialRepository.findByOwnerTypeAndOwnerId(OwnerType.EMPLOYEE, id).map(c -> c.getRole()).orElse(null);
		return EmployeeResponse.from(saved, role);
	}

	public Employee getScopedEntity(UUID id) {
		return findScoped(id);
	}

	private Employee findScoped(UUID id) {
		return employeeRepository.findByIdAndSchoolId(id, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Employee not found"));
	}

	private void applyRequest(Employee employee, EmployeeRequest request) {
		employee.setName(request.getName());
		employee.setDesignation(request.getDesignation());
		employee.setJoinDate(request.getJoinDate());
		// Optional fields: left out keeps the current value, "" clears it (see PartialUpdate).
		employee.setBankAccount(PartialUpdate.text(employee.getBankAccount(), request.getBankAccount()));
		employee.setContactPhone(PartialUpdate.text(employee.getContactPhone(), request.getContactPhone()));
		employee.setContactEmail(PartialUpdate.text(employee.getContactEmail(), request.getContactEmail()));
		employee.setEmployeeType(PartialUpdate.value(employee.getEmployeeType(), request.getEmployeeType()));
	}

	private static boolean isAdmin() {
		AuthPrincipal principal = AuthContext.currentOrNull();
		return principal != null && principal.getRole() == Role.ADMIN;
	}

	private static boolean isSelf(UUID employeeId) {
		AuthPrincipal principal = AuthContext.currentOrNull();
		return principal != null && principal.getOwnerType() == OwnerType.EMPLOYEE
				&& principal.getOwnerId().equals(employeeId);
	}

}
