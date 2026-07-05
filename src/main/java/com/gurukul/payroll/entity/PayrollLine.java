package com.gurukul.payroll.entity;

import com.gurukul.common.BaseEntity;
import com.gurukul.employees.entity.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "payroll_line", uniqueConstraints = @UniqueConstraint(columnNames = {"run_id", "employee_id"}))
public class PayrollLine extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "run_id", nullable = false)
	private PayrollRun run;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "employee_id", nullable = false)
	private Employee employee;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal gross;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal deductions;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal net;

}
