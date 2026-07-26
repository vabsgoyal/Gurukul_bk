package com.gurukul.employees.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Whether an employee is teaching or non-teaching staff")
public enum EmployeeType {

	@Schema(description = "Teaching staff (can be a class teacher / subject teacher)")
	TEACHING,

	@Schema(description = "Non-teaching staff (admin, accounts, support, etc.)")
	NON_TEACHING

}
