package com.gurukul.employees.service;

import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.employees.dto.EmployeeRequest;
import com.gurukul.employees.dto.EmployeeResponse;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.entity.EmployeeStatus;
import com.gurukul.employees.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeService {

	private final EmployeeRepository employeeRepository;
	private final SchoolContext schoolContext;

	public List<EmployeeResponse> list() {
		return employeeRepository.findAllBySchoolIdOrderByNameAsc(schoolContext.getSchoolId()).stream()
				.map(EmployeeResponse::from)
				.toList();
	}

	public EmployeeResponse getById(UUID id) {
		return EmployeeResponse.from(findScoped(id));
	}

	@Transactional
	public EmployeeResponse create(EmployeeRequest request) {
		Employee employee = new Employee();
		employee.setSchoolId(schoolContext.getSchoolId());
		applyRequest(employee, request);
		employee.setStatus(request.getStatus() != null ? request.getStatus() : EmployeeStatus.ACTIVE);
		return EmployeeResponse.from(employeeRepository.save(employee));
	}

	@Transactional
	public EmployeeResponse update(UUID id, EmployeeRequest request) {
		Employee employee = findScoped(id);
		applyRequest(employee, request);
		if (request.getStatus() != null) {
			employee.setStatus(request.getStatus());
		}
		return EmployeeResponse.from(employeeRepository.save(employee));
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
		employee.setBankAccount(request.getBankAccount());
		employee.setContactPhone(request.getContactPhone());
	}

}
