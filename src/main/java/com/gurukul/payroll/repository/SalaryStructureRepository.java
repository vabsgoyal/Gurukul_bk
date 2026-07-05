package com.gurukul.payroll.repository;

import com.gurukul.payroll.entity.SalaryStructure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SalaryStructureRepository extends JpaRepository<SalaryStructure, UUID> {

	List<SalaryStructure> findAllBySchoolId(UUID schoolId);

	List<SalaryStructure> findAllByEmployeeIdOrderByEffectiveFromDesc(UUID employeeId);

}
