package com.gurukul.employees.dto;

import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.entity.EmployeeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "Employee record")
public class EmployeeResponse {

	private UUID id;
	private UUID schoolId;
	private String name;
	private String designation;
	private LocalDate joinDate;
	private String bankAccount;
	private String contactPhone;
	private EmployeeStatus status;
	private Instant createdAt;
	private Instant updatedAt;

	public static EmployeeResponse from(Employee employee) {
		return new EmployeeResponse(
				employee.getId(),
				employee.getSchoolId(),
				employee.getName(),
				employee.getDesignation(),
				employee.getJoinDate(),
				employee.getBankAccount(),
				employee.getContactPhone(),
				employee.getStatus(),
				employee.getCreatedAt(),
				employee.getUpdatedAt()
		);
	}

}
