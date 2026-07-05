package com.gurukul.payroll.service;

import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.employees.entity.EmployeeStatus;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.employees.service.EmployeeService;
import com.gurukul.finance.entity.FinancialTransaction;
import com.gurukul.finance.entity.PaymentMethod;
import com.gurukul.finance.entity.SourceType;
import com.gurukul.finance.service.LedgerService;
import com.gurukul.payroll.dto.PayrollDtos;
import com.gurukul.payroll.entity.*;
import com.gurukul.payroll.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PayrollService {

	private final SalaryStructureRepository salaryStructureRepository;
	private final PayrollRunRepository payrollRunRepository;
	private final PayrollLineRepository payrollLineRepository;
	private final SalaryPaymentRepository salaryPaymentRepository;
	private final PayslipRepository payslipRepository;
	private final EmployeeRepository employeeRepository;
	private final EmployeeService employeeService;
	private final LedgerService ledgerService;
	private final SchoolContext schoolContext;

	@Transactional(readOnly = true)
	public List<PayrollDtos.SalaryStructureResponse> listSalaryStructures() {
		return salaryStructureRepository.findAllBySchoolId(schoolContext.getSchoolId()).stream()
				.map(PayrollDtos.SalaryStructureResponse::from).toList();
	}

	@Transactional
	public PayrollDtos.SalaryStructureResponse createSalaryStructure(PayrollDtos.SalaryStructureRequest request) {
		SalaryStructure structure = new SalaryStructure();
		structure.setSchoolId(schoolContext.getSchoolId());
		structure.setEmployee(employeeService.getScopedEntity(request.getEmployeeId()));
		structure.setBasic(request.getBasic());
		structure.setAllowances(request.getAllowances() != null ? request.getAllowances() : BigDecimal.ZERO);
		structure.setDeductions(request.getDeductions() != null ? request.getDeductions() : BigDecimal.ZERO);
		structure.setEffectiveFrom(request.getEffectiveFrom());
		return PayrollDtos.SalaryStructureResponse.from(salaryStructureRepository.save(structure));
	}

	@Transactional
	public PayrollDtos.PayrollRunResponse createRun(PayrollDtos.PayrollRunRequest request) {
		UUID schoolId = schoolContext.getSchoolId();
		if (payrollRunRepository.findBySchoolIdAndMonthAndYear(schoolId, request.getMonth(), request.getYear()).isPresent()) {
			throw new IllegalArgumentException("Payroll run already exists for this month");
		}
		PayrollRun run = new PayrollRun();
		run.setSchoolId(schoolId);
		run.setMonth(request.getMonth());
		run.setYear(request.getYear());
		run.setStatus(PayrollRunStatus.DRAFT);
		return PayrollDtos.PayrollRunResponse.from(payrollRunRepository.save(run));
	}

	@Transactional
	public PayrollDtos.PayrollRunResponse processRun(UUID runId) {
		PayrollRun run = findRun(runId);
		if (run.getStatus() != PayrollRunStatus.DRAFT) {
			throw new IllegalArgumentException("Only draft runs can be processed");
		}
		List<com.gurukul.employees.entity.Employee> employees = employeeRepository
				.findAllBySchoolIdOrderByNameAsc(schoolContext.getSchoolId()).stream()
				.filter(e -> e.getStatus() == EmployeeStatus.ACTIVE).toList();

		for (com.gurukul.employees.entity.Employee employee : employees) {
			salaryStructureRepository.findAllByEmployeeIdOrderByEffectiveFromDesc(employee.getId()).stream()
					.filter(s -> !s.getEffectiveFrom().isAfter(LocalDate.of(run.getYear(), run.getMonth(), 1)))
					.findFirst()
					.ifPresent(structure -> {
						BigDecimal gross = structure.getBasic().add(structure.getAllowances());
						BigDecimal net = gross.subtract(structure.getDeductions());
						PayrollLine line = new PayrollLine();
						line.setSchoolId(schoolContext.getSchoolId());
						line.setRun(run);
						line.setEmployee(employee);
						line.setGross(gross);
						line.setDeductions(structure.getDeductions());
						line.setNet(net);
						payrollLineRepository.save(line);
					});
		}
		run.setStatus(PayrollRunStatus.PROCESSED);
		return PayrollDtos.PayrollRunResponse.from(payrollRunRepository.save(run));
	}

	@Transactional
	public PayrollDtos.PayrollRunResponse payRun(UUID runId, PayrollDtos.PayRunRequest request) {
		PayrollRun run = findRun(runId);
		if (run.getStatus() != PayrollRunStatus.PROCESSED) {
			throw new IllegalArgumentException("Only processed runs can be paid");
		}
		List<PayrollLine> lines = payrollLineRepository.findAllByRunId(runId);
		for (PayrollLine line : lines) {
			if (salaryPaymentRepository.findByPayrollLineId(line.getId()).isPresent()) {
				continue;
			}
			FinancialTransaction tx = ledgerService.recordOutflow(
					SourceType.SALARY, line.getId(), line.getNet(),
					request.getPaymentMethod(), request.getPaymentReference(),
					request.getTransactionDate() != null ? request.getTransactionDate() : LocalDate.now(),
					null, "Salary: " + line.getEmployee().getName());

			SalaryPayment payment = new SalaryPayment();
			payment.setSchoolId(schoolContext.getSchoolId());
			payment.setPayrollLine(line);
			payment.setTransactionId(tx.getId());
			salaryPaymentRepository.save(payment);

			Payslip payslip = new Payslip();
			payslip.setSchoolId(schoolContext.getSchoolId());
			payslip.setPayrollLine(line);
			payslip.setDocumentRef("PAYSLIP-" + run.getYear() + "-" + run.getMonth() + "-" + line.getEmployee().getId());
			payslipRepository.save(payslip);
		}
		run.setStatus(PayrollRunStatus.PAID);
		return PayrollDtos.PayrollRunResponse.from(payrollRunRepository.save(run));
	}

	@Transactional(readOnly = true)
	public List<PayrollDtos.PayrollLineResponse> listRunLines(UUID runId) {
		findRun(runId);
		return payrollLineRepository.findAllByRunId(runId).stream()
				.map(PayrollDtos.PayrollLineResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public List<PayrollDtos.SalaryHistoryResponse> salaryHistory(UUID employeeId) {
		employeeService.getScopedEntity(employeeId);
		return payrollLineRepository.findAllByEmployeeId(employeeId).stream()
				.sorted(Comparator.comparing((PayrollLine l) -> l.getRun().getYear()).reversed()
						.thenComparing(l -> l.getRun().getMonth(), Comparator.reverseOrder()))
				.map(line -> new PayrollDtos.SalaryHistoryResponse(
						line.getId(), line.getRun().getMonth(), line.getRun().getYear(),
						line.getNet(), line.getRun().getStatus()))
				.toList();
	}

	@Transactional(readOnly = true)
	public PayrollDtos.PayslipResponse getPayslip(UUID lineId) {
		PayrollLine line = payrollLineRepository.findByIdAndSchoolId(lineId, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Payroll line not found"));
		Payslip payslip = payslipRepository.findByPayrollLineId(lineId)
				.orElseThrow(() -> new EntityNotFoundException("Payslip not found"));
		return new PayrollDtos.PayslipResponse(
				line.getId(), line.getEmployee().getId(), line.getEmployee().getName(),
				line.getNet(), payslip.getDocumentRef());
	}

	private PayrollRun findRun(UUID runId) {
		return payrollRunRepository.findByIdAndSchoolId(runId, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Payroll run not found"));
	}

}
