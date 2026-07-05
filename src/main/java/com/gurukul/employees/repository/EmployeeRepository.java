package com.gurukul.employees.repository;

import com.gurukul.employees.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

	List<Employee> findAllBySchoolIdOrderByNameAsc(UUID schoolId);

	Optional<Employee> findByIdAndSchoolId(UUID id, UUID schoolId);

}
