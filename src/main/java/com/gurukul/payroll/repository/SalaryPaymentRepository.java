package com.gurukul.payroll.repository;

import com.gurukul.payroll.entity.SalaryPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SalaryPaymentRepository extends JpaRepository<SalaryPayment, UUID> {

	Optional<SalaryPayment> findByPayrollLineId(UUID payrollLineId);

}
