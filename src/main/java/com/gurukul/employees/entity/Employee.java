package com.gurukul.employees.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "employee")
public class Employee extends BaseEntity {

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String designation;

	@Column(name = "join_date", nullable = false)
	private LocalDate joinDate;

	@Column(name = "bank_account")
	private String bankAccount;

	@Column(name = "contact_phone")
	private String contactPhone;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EmployeeStatus status;

}
