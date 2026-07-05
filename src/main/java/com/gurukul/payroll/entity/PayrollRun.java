package com.gurukul.payroll.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "payroll_run", uniqueConstraints = @UniqueConstraint(columnNames = {"school_id", "month", "year"}))
public class PayrollRun extends BaseEntity {

	@Column(name = "payroll_month", nullable = false)
	private int month;

	@Column(name = "payroll_year", nullable = false)
	private int year;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PayrollRunStatus status;

}
