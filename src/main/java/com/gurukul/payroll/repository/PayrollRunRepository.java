package com.gurukul.payroll.repository;

import com.gurukul.payroll.entity.PayrollRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, UUID> {

	Optional<PayrollRun> findBySchoolIdAndMonthAndYear(UUID schoolId, int month, int year);

	Optional<PayrollRun> findByIdAndSchoolId(UUID id, UUID schoolId);

	List<PayrollRun> findAllBySchoolIdAndYear(UUID schoolId, int year);

}
