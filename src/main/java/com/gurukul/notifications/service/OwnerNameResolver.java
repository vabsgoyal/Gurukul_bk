package com.gurukul.notifications.service;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.parents.entity.Parent;
import com.gurukul.parents.repository.ParentRepository;
import com.gurukul.students.entity.Student;
import com.gurukul.students.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Display name of whoever caused a push ("Priya Sharma is calling") - push titles read much better
 * with a person's name than a generic "New message". Empty when the owner can't be found, so the
 * caller picks its own neutral fallback wording.
 */
@Component
@RequiredArgsConstructor
public class OwnerNameResolver {

	private final EmployeeRepository employeeRepository;
	private final StudentRepository studentRepository;
	private final ParentRepository parentRepository;

	public Optional<String> nameOf(UUID schoolId, OwnerType ownerType, UUID ownerId) {
		if (ownerType == null || ownerId == null) {
			return Optional.empty();
		}
		Optional<String> name = switch (ownerType) {
			case EMPLOYEE -> employeeRepository.findByIdAndSchoolId(ownerId, schoolId).map(Employee::getName);
			case STUDENT -> studentRepository.findByIdAndSchoolId(ownerId, schoolId).map(Student::getName);
			case PARENT -> parentRepository.findByIdAndSchoolId(ownerId, schoolId).map(Parent::getName);
		};
		return name.filter(n -> !n.isBlank());
	}

}
