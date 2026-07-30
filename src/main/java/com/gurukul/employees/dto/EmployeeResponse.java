package com.gurukul.employees.dto;

import com.gurukul.auth.entity.Role;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.entity.EmployeeStatus;
import com.gurukul.employees.entity.EmployeeType;
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
	private String contactEmail;
	private EmployeeStatus status;
	private EmployeeType employeeType;

	@Schema(description = "Login role, if credentials have been provisioned for this employee (null otherwise)")
	private Role role;

	private Instant createdAt;
	private Instant updatedAt;

	public static EmployeeResponse from(Employee employee, Role role) {
		return new EmployeeResponse(
				employee.getId(),
				employee.getSchoolId(),
				employee.getName(),
				employee.getDesignation(),
				employee.getJoinDate(),
				employee.getBankAccount(),
				employee.getContactPhone(),
				employee.getContactEmail(),
				employee.getStatus(),
				employee.getEmployeeType(),
				role,
				employee.getCreatedAt(),
				employee.getUpdatedAt()
		);
	}

}
