package com.gurukul.employees.repository;

import com.gurukul.employees.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

	List<Employee> findAllBySchoolIdOrderByNameAsc(UUID schoolId);

	Page<Employee> findAllBySchoolIdOrderByNameAsc(UUID schoolId, Pageable pageable);

	Optional<Employee> findByIdAndSchoolId(UUID id, UUID schoolId);

	List<Employee> findAllBySchoolIdAndContactPhone(UUID schoolId, String contactPhone);

}
