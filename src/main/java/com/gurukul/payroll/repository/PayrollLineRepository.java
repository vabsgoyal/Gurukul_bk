package com.gurukul.payroll.repository;

import com.gurukul.payroll.entity.PayrollLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayrollLineRepository extends JpaRepository<PayrollLine, UUID> {

	List<PayrollLine> findAllByRunId(UUID runId);

	Optional<PayrollLine> findByIdAndSchoolId(UUID id, UUID schoolId);

	List<PayrollLine> findAllByEmployeeId(UUID employeeId);

}
