package com.gurukul.employees.dto;

import com.gurukul.employees.entity.EmployeeStatus;
import com.gurukul.employees.entity.EmployeeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Schema(description = "Employee create/update payload")
public class EmployeeRequest {

	@NotBlank
	@Schema(description = "Employee name", example = "Priya Singh")
	private String name;

	@NotBlank
	@Schema(description = "Designation", example = "Teacher")
	private String designation;

	@NotNull
	@Schema(description = "Join date", example = "2024-04-01")
	private LocalDate joinDate;

	@Schema(description = "Bank account number")
	private String bankAccount;

	@Schema(description = "Contact phone", example = "9876543210")
	private String contactPhone;

	@Email
	@Schema(description = "Contact email", example = "priya.singh@school.example")
	private String contactEmail;

	@Schema(description = "Employee status", example = "ACTIVE")
	private EmployeeStatus status;

	@Schema(description = "Teaching or non-teaching staff", example = "TEACHING")
	private EmployeeType employeeType;

}
