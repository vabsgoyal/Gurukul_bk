package com.gurukul.employees.repository;

import com.gurukul.employees.entity.EmployeeFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeFeedbackRepository extends JpaRepository<EmployeeFeedback, UUID> {

	List<EmployeeFeedback> findAllBySchoolIdAndEmployeeIdOrderByFeedbackDateDesc(UUID schoolId, UUID employeeId);

	Optional<EmployeeFeedback> findByIdAndSchoolId(UUID id, UUID schoolId);

}
