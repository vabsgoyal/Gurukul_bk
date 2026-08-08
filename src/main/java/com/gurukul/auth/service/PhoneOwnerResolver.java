package com.gurukul.auth.service;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.students.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves a phone number to whichever Employee/Student owns it within a school - shared by
 * {@link OtpService} (dummy OTP) and Supabase-verified phone login, since both need the exact
 * same lookup.
 */
@Component
@RequiredArgsConstructor
public class PhoneOwnerResolver {

	private final EmployeeRepository employeeRepository;
	private final StudentRepository studentRepository;

	public record PhoneOwner(OwnerType ownerType, UUID ownerId) {
	}

	// If a phone number matches more than one record (e.g. siblings sharing a parent's number),
	// the first match wins - there's no "choose which profile" step yet.
	public PhoneOwner resolve(UUID schoolId, String phone) {
		return employeeRepository.findAllBySchoolIdAndContactPhone(schoolId, phone).stream().findFirst()
				.map(employee -> new PhoneOwner(OwnerType.EMPLOYEE, employee.getId()))
				.or(() -> studentRepository.findAllBySchoolIdAndParentContact(schoolId, phone).stream().findFirst()
						.map(student -> new PhoneOwner(OwnerType.STUDENT, student.getId())))
				.orElseThrow(() -> new EntityNotFoundException("Phone number not registered"));
	}

}
