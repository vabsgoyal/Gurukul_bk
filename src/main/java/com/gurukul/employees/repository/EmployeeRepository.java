package com.gurukul.employees.repository;

import com.gurukul.employees.entity.Employee;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

	List<Employee> findAllBySchoolIdOrderByNameAsc(UUID schoolId);

	/** Slice, not Page: avoids Spring Data's automatic separate COUNT(*) query on every page - see
	 *  StudentRepository's equivalent note. Total count fetched separately only on page 0. */
	Slice<Employee> findAllBySchoolIdOrderByNameAsc(UUID schoolId, Pageable pageable);

	Optional<Employee> findByIdAndSchoolId(UUID id, UUID schoolId);

	List<Employee> findAllBySchoolIdAndContactPhone(UUID schoolId, String contactPhone);

	long countBySchoolId(UUID schoolId);

}
