package com.gurukul.employees.repository;

import com.gurukul.employees.entity.Employee;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

	/** Whether any of these phones already logs in as an ADMIN of some school. */
	@Query("""
			select count(e) > 0 from Employee e, com.gurukul.auth.entity.Credential c
			where c.ownerType = com.gurukul.auth.entity.OwnerType.EMPLOYEE and c.ownerId = e.id
			  and c.role = com.gurukul.auth.entity.Role.ADMIN and e.contactPhone in :phones
			""")
	boolean existsAdminWithContactPhoneIn(@Param("phones") Collection<String> phones);

	List<Employee> findAllBySchoolIdAndIdIn(UUID schoolId, Collection<UUID> ids);

}
