package com.gurukul.attendance.service;

import com.gurukul.attendance.dto.AttendanceDeviceDtos.DeviceEventRequest;
import com.gurukul.attendance.dto.AttendanceDeviceDtos.DeviceEventResponse;
import com.gurukul.attendance.entity.AttendanceDevice;
import com.gurukul.attendance.entity.AttendanceIdentifier;
import com.gurukul.attendance.entity.AttendanceStatus;
import com.gurukul.attendance.repository.AttendanceDeviceRepository;
import com.gurukul.attendance.repository.AttendanceIdentifierRepository;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.students.entity.Student;
import com.gurukul.students.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The device-to-server ingestion endpoint's logic. No Spring Security role model applies here - a
 * physical unmanned device has no human JWT - so authentication is a manual shared-secret check,
 * copying the exact pattern already used by OpsController/AdminBackfillService: throw
 * BadCredentialsException (already mapped to 401 in GlobalExceptionHandler) on a bad/missing key.
 * The device's own X-School-Id header drives normal SchoolContext resolution, so a device can only
 * ever resolve identifiers scoped to its own school - cross-school data is never reachable even if a
 * key were somehow guessed for the wrong school context.
 */
@Service
@RequiredArgsConstructor
public class AttendanceDeviceEventService {

	private final AttendanceDeviceRepository attendanceDeviceRepository;
	private final AttendanceIdentifierRepository attendanceIdentifierRepository;
	private final StudentRepository studentRepository;
	private final EmployeeRepository employeeRepository;
	private final AttendanceService attendanceService;
	private final StaffAttendanceService staffAttendanceService;
	private final PasswordEncoder passwordEncoder;
	private final SchoolContext schoolContext;

	@Transactional
	public DeviceEventResponse recordEvent(String providedKey, DeviceEventRequest request) {
		UUID schoolId = schoolContext.getSchoolId();
		AttendanceDevice device = resolveDevice(schoolId, providedKey);
		device.setLastSeenAt(Instant.now());
		attendanceDeviceRepository.save(device);

		// request.getCapturedAt() is accepted for vendor-payload compatibility but intentionally
		// unused for the attendance date itself - like self-mark, the server clock is authoritative
		// and a client-claimed timestamp is never trusted for that.
		AttendanceIdentifier identifier = attendanceIdentifierRepository
				.findBySchoolIdAndMethodAndExternalIdAndActiveTrue(schoolId, device.getDeviceType(), request.getExternalId())
				.orElseThrow(() -> new EntityNotFoundException("No one is enrolled with this identifier"));

		if (identifier.getOwnerType() == OwnerType.STUDENT) {
			Student student = studentRepository.findByIdAndSchoolId(identifier.getOwnerId(), schoolId)
					.orElseThrow(() -> new EntityNotFoundException("Enrolled student not found"));
			attendanceService.markByDevice(student, device);
			return new DeviceEventResponse(OwnerType.STUDENT, student.getId(), student.getName(), AttendanceStatus.PRESENT, LocalDate.now());
		}

		Employee employee = employeeRepository.findByIdAndSchoolId(identifier.getOwnerId(), schoolId)
				.orElseThrow(() -> new EntityNotFoundException("Enrolled employee not found"));
		staffAttendanceService.markByDevice(employee, device);
		return new DeviceEventResponse(OwnerType.EMPLOYEE, employee.getId(), employee.getName(), AttendanceStatus.PRESENT, LocalDate.now());
	}

	private AttendanceDevice resolveDevice(UUID schoolId, String providedKey) {
		if (providedKey == null || providedKey.isBlank()) {
			throw new BadCredentialsException("Missing device key");
		}
		return attendanceDeviceRepository.findAllBySchoolIdAndActiveTrue(schoolId).stream()
				.filter(device -> passwordEncoder.matches(providedKey, device.getApiKeyHash()))
				.findFirst()
				.orElseThrow(() -> new BadCredentialsException("Invalid or inactive device key"));
	}

}
