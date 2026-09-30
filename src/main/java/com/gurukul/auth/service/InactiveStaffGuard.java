package com.gurukul.auth.service;

import com.gurukul.auth.entity.Credential;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.employees.entity.EmployeeStatus;
import com.gurukul.employees.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Staff marked INACTIVE (former teachers kept for the records) can't log in by any route - OTP,
 * password, Google, or renewing an existing session. Checked on the employee record, not the
 * credential, because OTP creates a credential on first login for anyone whose phone is on file.
 */
@Component
@RequiredArgsConstructor
public class InactiveStaffGuard {

	private final EmployeeRepository employeeRepository;

	public boolean isInactiveStaff(OwnerType ownerType, UUID ownerId) {
		return ownerType == OwnerType.EMPLOYEE && employeeRepository.findById(ownerId)
				.map(employee -> employee.getStatus() == EmployeeStatus.INACTIVE)
				.orElse(false);
	}

	public boolean isInactiveStaff(Credential credential) {
		return isInactiveStaff(credential.getOwnerType(), credential.getOwnerId());
	}

}
