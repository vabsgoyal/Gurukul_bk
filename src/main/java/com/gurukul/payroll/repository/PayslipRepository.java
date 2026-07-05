package com.gurukul.payroll.repository;

import com.gurukul.payroll.entity.Payslip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PayslipRepository extends JpaRepository<Payslip, UUID> {

	Optional<Payslip> findByPayrollLineId(UUID payrollLineId);

}
