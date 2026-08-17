package com.gurukul.attendance.service;

import com.gurukul.attendance.dto.AttendanceDeviceDtos.EnrollIdentifierRequest;
import com.gurukul.attendance.dto.AttendanceDeviceDtos.IdentifierResponse;
import com.gurukul.attendance.entity.AttendanceIdentifier;
import com.gurukul.attendance.repository.AttendanceIdentifierRepository;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.service.EmployeeService;
import com.gurukul.students.entity.Student;
import com.gurukul.students.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Enrolls/lists/removes the mapping between a device-reported external id (RFID UID, fingerprint
 * template id, face-recognition subject id) and a student/employee. Uniqueness is enforced here
 * (not a DB constraint) - see the V49 migration comment for why.
 */
@Service
@RequiredArgsConstructor
public class AttendanceIdentifierService {

	private final AttendanceIdentifierRepository attendanceIdentifierRepository;
	private final StudentService studentService;
	private final EmployeeService employeeService;
	private final SchoolContext schoolContext;

	@Transactional
	public IdentifierResponse enrollForStudent(UUID studentId, EnrollIdentifierRequest request) {
		Student student = studentService.getScopedEntity(studentId);
		AttendanceIdentifier identifier = enroll(OwnerType.STUDENT, student.getId(), request);
		return IdentifierResponse.from(identifier, student.getName());
	}

	@Transactional
	public IdentifierResponse enrollForEmployee(UUID employeeId, EnrollIdentifierRequest request) {
		Employee employee = employeeService.getScopedEntity(employeeId);
		AttendanceIdentifier identifier = enroll(OwnerType.EMPLOYEE, employee.getId(), request);
		return IdentifierResponse.from(identifier, employee.getName());
	}

	@Transactional(readOnly = true)
	public List<IdentifierResponse> listForStudent(UUID studentId) {
		Student student = studentService.getScopedEntity(studentId);
		return list(OwnerType.STUDENT, student.getId(), student.getName());
	}

	@Transactional(readOnly = true)
	public List<IdentifierResponse> listForEmployee(UUID employeeId) {
		Employee employee = employeeService.getScopedEntity(employeeId);
		return list(OwnerType.EMPLOYEE, employee.getId(), employee.getName());
	}

	@Transactional
	public void remove(UUID identifierId) {
		AttendanceIdentifier identifier = attendanceIdentifierRepository
				.findByIdAndSchoolId(identifierId, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Attendance identifier not found"));
		identifier.setActive(false);
		attendanceIdentifierRepository.save(identifier);
	}

	private AttendanceIdentifier enroll(OwnerType ownerType, UUID ownerId, EnrollIdentifierRequest request) {
		UUID schoolId = schoolContext.getSchoolId();
		if (attendanceIdentifierRepository.existsBySchoolIdAndMethodAndExternalIdAndActiveTrue(
				schoolId, request.getMethod(), request.getExternalId())) {
			throw new IllegalArgumentException("This identifier is already enrolled to someone else");
		}
		if (attendanceIdentifierRepository.existsBySchoolIdAndOwnerTypeAndOwnerIdAndMethodAndActiveTrue(
				schoolId, ownerType, ownerId, request.getMethod())) {
			throw new IllegalArgumentException("This person already has an active identifier for this method - remove it first");
		}

		AttendanceIdentifier identifier = new AttendanceIdentifier();
		identifier.setSchoolId(schoolId);
		identifier.setOwnerType(ownerType);
		identifier.setOwnerId(ownerId);
		identifier.setMethod(request.getMethod());
		identifier.setExternalId(request.getExternalId());
		identifier.setActive(true);
		return attendanceIdentifierRepository.save(identifier);
	}

	private List<IdentifierResponse> list(OwnerType ownerType, UUID ownerId, String ownerName) {
		return attendanceIdentifierRepository
				.findAllBySchoolIdAndOwnerTypeAndOwnerId(schoolContext.getSchoolId(), ownerType, ownerId).stream()
				.filter(AttendanceIdentifier::isActive)
				.map(identifier -> IdentifierResponse.from(identifier, ownerName))
				.toList();
	}

}
