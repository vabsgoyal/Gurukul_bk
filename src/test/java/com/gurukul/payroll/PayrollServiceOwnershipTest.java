package com.gurukul.payroll;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.SchoolContext;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.employees.service.EmployeeService;
import com.gurukul.finance.service.LedgerService;
import com.gurukul.payroll.dto.PayrollDtos;
import com.gurukul.payroll.entity.PayrollLine;
import com.gurukul.payroll.entity.PayrollRun;
import com.gurukul.payroll.entity.Payslip;
import com.gurukul.payroll.repository.PayrollLineRepository;
import com.gurukul.payroll.repository.PayrollRunRepository;
import com.gurukul.payroll.repository.PayslipRepository;
import com.gurukul.payroll.repository.SalaryPaymentRepository;
import com.gurukul.payroll.repository.SalaryStructureRepository;
import com.gurukul.payroll.service.PayrollService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Focused unit tests for the TEACHER self-only ownership check added to
 * {@link PayrollService#salaryHistory} / {@code getPayslip} - added alongside
 * {@code Gurukul_bk#75}'s SecurityConfig role gate (TEACHER+ADMIN) since the role gate alone doesn't
 * stop a TEACHER from passing another employee's id. Mirrors
 * {@code com.gurukul.fees.FeePaymentServiceOwnershipTest}'s Mockito-direct style.
 */
class PayrollServiceOwnershipTest {

	private static final UUID SCHOOL_ID = UUID.randomUUID();
	private static final UUID OWNING_EMPLOYEE_ID = UUID.randomUUID();
	private static final UUID OTHER_EMPLOYEE_ID = UUID.randomUUID();
	private static final UUID PAYROLL_LINE_ID = UUID.randomUUID();

	private PayrollLineRepository payrollLineRepository;
	private PayslipRepository payslipRepository;
	private EmployeeService employeeService;
	private PayrollService payrollService;

	@BeforeEach
	void setUp() {
		payrollLineRepository = mock(PayrollLineRepository.class);
		payslipRepository = mock(PayslipRepository.class);
		employeeService = mock(EmployeeService.class);
		SchoolContext schoolContext = new SchoolContext();
		schoolContext.setSchoolId(SCHOOL_ID);

		payrollService = new PayrollService(
				mock(SalaryStructureRepository.class),
				mock(PayrollRunRepository.class),
				payrollLineRepository,
				mock(SalaryPaymentRepository.class),
				payslipRepository,
				mock(EmployeeRepository.class),
				employeeService,
				mock(LedgerService.class),
				schoolContext);

		Employee owningEmployee = new Employee();
		owningEmployee.setId(OWNING_EMPLOYEE_ID);
		owningEmployee.setName("Owning Teacher");
		when(employeeService.getScopedEntity(OWNING_EMPLOYEE_ID)).thenReturn(owningEmployee);

		PayrollRun run = new PayrollRun();
		run.setId(UUID.randomUUID());
		run.setMonth(8);
		run.setYear(2026);

		PayrollLine line = new PayrollLine();
		line.setId(PAYROLL_LINE_ID);
		line.setRun(run);
		line.setEmployee(owningEmployee);
		line.setGross(new java.math.BigDecimal("50000.00"));
		line.setDeductions(new java.math.BigDecimal("5000.00"));
		line.setNet(new java.math.BigDecimal("45000.00"));
		when(payrollLineRepository.findByIdAndSchoolId(PAYROLL_LINE_ID, SCHOOL_ID)).thenReturn(Optional.of(line));
		when(payrollLineRepository.findAllByEmployeeId(OWNING_EMPLOYEE_ID)).thenReturn(List.of(line));

		Payslip payslip = new Payslip();
		payslip.setId(UUID.randomUUID());
		payslip.setPayrollLine(line);
		payslip.setDocumentRef("PAYSLIP-2026-8-" + OWNING_EMPLOYEE_ID);
		when(payslipRepository.findByPayrollLineId(PAYROLL_LINE_ID)).thenReturn(Optional.of(payslip));
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	private void authenticateAs(UUID ownerId, Role role) {
		AuthPrincipal principal = new AuthPrincipal(ownerId, OwnerType.EMPLOYEE, role, SCHOOL_ID, "test-user");
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of()));
	}

	@Test
	void teacherCanViewOwnSalaryHistory() {
		authenticateAs(OWNING_EMPLOYEE_ID, Role.TEACHER);

		List<PayrollDtos.SalaryHistoryResponse> history = payrollService.salaryHistory(OWNING_EMPLOYEE_ID);

		assertThat(history).hasSize(1);
	}

	@Test
	void teacherCannotViewSomeoneElsesSalaryHistory() {
		authenticateAs(OTHER_EMPLOYEE_ID, Role.TEACHER);

		assertThatThrownBy(() -> payrollService.salaryHistory(OWNING_EMPLOYEE_ID))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void adminCanViewAnyEmployeesSalaryHistory() {
		authenticateAs(UUID.randomUUID(), Role.ADMIN);

		List<PayrollDtos.SalaryHistoryResponse> history = payrollService.salaryHistory(OWNING_EMPLOYEE_ID);

		assertThat(history).hasSize(1);
	}

	@Test
	void teacherCanViewOwnPayslip() {
		authenticateAs(OWNING_EMPLOYEE_ID, Role.TEACHER);

		PayrollDtos.PayslipResponse payslip = payrollService.getPayslip(PAYROLL_LINE_ID);

		assertThat(payslip.getEmployeeId()).isEqualTo(OWNING_EMPLOYEE_ID);
	}

	@Test
	void teacherCannotViewSomeoneElsesPayslip() {
		authenticateAs(OTHER_EMPLOYEE_ID, Role.TEACHER);

		assertThatThrownBy(() -> payrollService.getPayslip(PAYROLL_LINE_ID))
				.isInstanceOf(AccessDeniedException.class);
	}

}
