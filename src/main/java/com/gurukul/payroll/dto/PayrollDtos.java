package com.gurukul.payroll.dto;

import com.gurukul.finance.entity.PaymentMethod;
import com.gurukul.payroll.entity.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class PayrollDtos {

	@Getter @Setter
	public static class SalaryStructureRequest {
		@NotNull private UUID employeeId;
		@NotNull @DecimalMin("0.01") private BigDecimal basic;
		private BigDecimal allowances;
		private BigDecimal deductions;
		@NotNull private LocalDate effectiveFrom;
	}

	@Getter @AllArgsConstructor
	public static class SalaryStructureResponse {
		private UUID id;
		private UUID employeeId;
		private String employeeName;
		private BigDecimal basic;
		private BigDecimal allowances;
		private BigDecimal deductions;
		private LocalDate effectiveFrom;

		public static SalaryStructureResponse from(SalaryStructure s) {
			return new SalaryStructureResponse(s.getId(), s.getEmployee().getId(), s.getEmployee().getName(),
					s.getBasic(), s.getAllowances(), s.getDeductions(), s.getEffectiveFrom());
		}
	}

	@Getter @Setter
	public static class PayrollRunRequest {
		@Min(1) @Max(12) private int month;
		@Min(2000) private int year;
	}

	@Getter @AllArgsConstructor
	public static class PayrollRunResponse {
		private UUID id;
		private int month;
		private int year;
		private PayrollRunStatus status;

		public static PayrollRunResponse from(PayrollRun run) {
			return new PayrollRunResponse(run.getId(), run.getMonth(), run.getYear(), run.getStatus());
		}
	}

	@Getter @AllArgsConstructor
	public static class PayrollLineResponse {
		private UUID id;
		private UUID employeeId;
		private String employeeName;
		private BigDecimal gross;
		private BigDecimal deductions;
		private BigDecimal net;

		public static PayrollLineResponse from(PayrollLine line) {
			return new PayrollLineResponse(line.getId(), line.getEmployee().getId(), line.getEmployee().getName(),
					line.getGross(), line.getDeductions(), line.getNet());
		}
	}

	@Getter @Setter
	public static class PayRunRequest {
		@NotNull private PaymentMethod paymentMethod;
		private String paymentReference;
		private LocalDate transactionDate;
	}

	@Getter @AllArgsConstructor
	public static class PayslipResponse {
		private UUID payrollLineId;
		private UUID employeeId;
		private String employeeName;
		private BigDecimal net;
		private String documentRef;
	}

	@Getter @AllArgsConstructor
	public static class SalaryHistoryResponse {
		private UUID payrollLineId;
		private int month;
		private int year;
		private BigDecimal net;
		private PayrollRunStatus runStatus;
	}

}
