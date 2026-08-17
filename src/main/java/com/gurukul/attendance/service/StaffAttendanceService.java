package com.gurukul.attendance.service;

import com.gurukul.attendance.dto.StaffAttendanceDtos.BulkStaffAttendanceRequest;
import com.gurukul.attendance.dto.StaffAttendanceDtos.EmployeeAttendanceHistoryResponse;
import com.gurukul.attendance.dto.StaffAttendanceDtos.SelfMarkAttendanceRequest;
import com.gurukul.attendance.dto.StaffAttendanceDtos.StaffAttendanceEntryRequest;
import com.gurukul.attendance.dto.StaffAttendanceDtos.StaffAttendanceEntryResponse;
import com.gurukul.attendance.dto.StaffAttendanceDtos.StaffAttendanceRecordResponse;
import com.gurukul.attendance.dto.StaffAttendanceDtos.StaffAttendanceRosterResponse;
import com.gurukul.attendance.entity.AttendanceDevice;
import com.gurukul.attendance.entity.AttendanceStatus;
import com.gurukul.attendance.entity.StaffAttendanceRecord;
import com.gurukul.attendance.repository.StaffAttendanceRecordRepository;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthContext;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.GeoUtils;
import com.gurukul.common.SchoolContext;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.employees.service.EmployeeService;
import com.gurukul.schools.entity.School;
import com.gurukul.schools.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffAttendanceService {

	private final StaffAttendanceRecordRepository staffAttendanceRecordRepository;
	private final EmployeeRepository employeeRepository;
	private final EmployeeService employeeService;
	private final SchoolRepository schoolRepository;
	private final SchoolContext schoolContext;

	@Transactional
	public StaffAttendanceRosterResponse markStaffAttendance(BulkStaffAttendanceRequest request) {
		UUID schoolId = schoolContext.getSchoolId();
		UUID markerId = request.getMarkedByEmployeeId() != null
				? request.getMarkedByEmployeeId()
				: AuthContext.current().getOwnerId();
		Employee markedBy = employeeService.getScopedEntity(markerId);

		for (StaffAttendanceEntryRequest entry : request.getRecords()) {
			Employee employee = employeeService.getScopedEntity(entry.getEmployeeId());

			StaffAttendanceRecord record = staffAttendanceRecordRepository
					.findBySchoolIdAndEmployeeIdAndAttendanceDate(schoolId, employee.getId(), request.getDate())
					.orElseGet(() -> {
						StaffAttendanceRecord newRecord = new StaffAttendanceRecord();
						newRecord.setSchoolId(schoolId);
						newRecord.setEmployee(employee);
						newRecord.setAttendanceDate(request.getDate());
						return newRecord;
					});
			record.setStatus(entry.getStatus());
			record.setMarkedByEmployee(markedBy);
			record.setRemarks(entry.getRemarks());
			record.setSelfMarked(false);
			record.setMarkedLatitude(null);
			record.setMarkedLongitude(null);
			record.setMarkedAccuracyMeters(null);
			record.setMarkedByDevice(null);
			record.setMethod(null);
			staffAttendanceRecordRepository.save(record);
		}

		return getStaffRoster(request.getDate());
	}

	/**
	 * A teacher (or admin) checking in from the school premises - always resolves the employee
	 * from the authenticated session, never a request parameter, since trusting a caller-supplied
	 * employeeId would let anyone mark someone else present. The distance check happens here,
	 * server-side, against the school's stored coordinates - the client's own "am I inside the
	 * fence" belief is never trusted, since GPS coordinates are trivially spoofable.
	 */
	@Transactional
	public StaffAttendanceRecordResponse selfMark(SelfMarkAttendanceRequest request) {
		UUID schoolId = schoolContext.getSchoolId();
		AuthPrincipal principal = AuthContext.current();
		Employee employee = employeeService.getScopedEntity(principal.getOwnerId());

		School school = schoolRepository.findById(schoolId)
				.orElseThrow(() -> new EntityNotFoundException("School not found"));
		if (school.getLatitude() == null || school.getLongitude() == null) {
			throw new IllegalStateException("School location has not been configured yet - ask an admin to set it before self-marking attendance");
		}

		double distanceMeters = GeoUtils.distanceMeters(
				school.getLatitude(), school.getLongitude(), request.getLatitude(), request.getLongitude());
		int radiusMeters = school.getGeofenceRadiusMeters();
		if (distanceMeters > radiusMeters) {
			throw new IllegalStateException(String.format(Locale.ROOT,
					"You are %.0fm away from the school; you must be within %dm to mark attendance", distanceMeters, radiusMeters));
		}

		LocalDate today = LocalDate.now();
		StaffAttendanceRecord record = staffAttendanceRecordRepository
				.findBySchoolIdAndEmployeeIdAndAttendanceDate(schoolId, employee.getId(), today)
				.orElseGet(() -> {
					StaffAttendanceRecord newRecord = new StaffAttendanceRecord();
					newRecord.setSchoolId(schoolId);
					newRecord.setEmployee(employee);
					newRecord.setAttendanceDate(today);
					return newRecord;
				});
		record.setStatus(AttendanceStatus.PRESENT);
		record.setMarkedByEmployee(employee);
		record.setSelfMarked(true);
		record.setMarkedLatitude(request.getLatitude());
		record.setMarkedLongitude(request.getLongitude());
		record.setMarkedAccuracyMeters(request.getAccuracy());
		record.setMarkedByDevice(null);
		record.setMethod(null);
		StaffAttendanceRecord saved = staffAttendanceRecordRepository.save(record);
		return StaffAttendanceRecordResponse.from(saved);
	}

	/**
	 * Called by AttendanceDeviceEventService when a registered RFID/fingerprint/face device scans an
	 * employee. No geofence check here (unlike selfMark) - the device itself is bolted to the school
	 * premises, so a successful, authenticated device event already implies on-site presence.
	 */
	@Transactional
	public StaffAttendanceRecordResponse markByDevice(Employee employee, AttendanceDevice device) {
		UUID schoolId = schoolContext.getSchoolId();
		LocalDate today = LocalDate.now();
		StaffAttendanceRecord record = staffAttendanceRecordRepository
				.findBySchoolIdAndEmployeeIdAndAttendanceDate(schoolId, employee.getId(), today)
				.orElseGet(() -> {
					StaffAttendanceRecord newRecord = new StaffAttendanceRecord();
					newRecord.setSchoolId(schoolId);
					newRecord.setEmployee(employee);
					newRecord.setAttendanceDate(today);
					return newRecord;
				});
		record.setStatus(AttendanceStatus.PRESENT);
		record.setMarkedByEmployee(null);
		record.setSelfMarked(false);
		record.setMarkedLatitude(null);
		record.setMarkedLongitude(null);
		record.setMarkedAccuracyMeters(null);
		record.setMarkedByDevice(device);
		record.setMethod(device.getDeviceType());
		StaffAttendanceRecord saved = staffAttendanceRecordRepository.save(record);
		return StaffAttendanceRecordResponse.from(saved);
	}

	public StaffAttendanceRosterResponse getStaffRoster(LocalDate date) {
		UUID schoolId = schoolContext.getSchoolId();
		List<Employee> employees = employeeRepository.findAllBySchoolIdOrderByNameAsc(schoolId);
		Map<UUID, StaffAttendanceRecord> recordsByEmployeeId = staffAttendanceRecordRepository
				.findAllBySchoolIdAndAttendanceDate(schoolId, date).stream()
				.collect(Collectors.toMap(r -> r.getEmployee().getId(), Function.identity()));

		List<StaffAttendanceEntryResponse> entries = employees.stream()
				.map(employee -> {
					StaffAttendanceRecord record = recordsByEmployeeId.get(employee.getId());
					return new StaffAttendanceEntryResponse(
							employee.getId(),
							employee.getName(),
							employee.getDesignation(),
							record != null ? record.getStatus() : null,
							record != null ? record.getRemarks() : null,
							record != null && record.isSelfMarked(),
							record != null ? record.getMethod() : null
					);
				})
				.toList();

		return new StaffAttendanceRosterResponse(date, entries);
	}

	@Transactional(readOnly = true)
	public EmployeeAttendanceHistoryResponse getEmployeeHistory(UUID employeeId, LocalDate from, LocalDate to) {
		AuthPrincipal principal = AuthContext.current();
		if (principal.getRole() == Role.TEACHER && !principal.getOwnerId().equals(employeeId)) {
			throw new AccessDeniedException("You can only view your own attendance");
		}

		UUID schoolId = schoolContext.getSchoolId();
		Employee employee = employeeRepository.findByIdAndSchoolId(employeeId, schoolId)
				.orElseThrow(() -> new EntityNotFoundException("Employee not found"));

		List<StaffAttendanceRecord> records = (from != null && to != null)
				? staffAttendanceRecordRepository.findAllBySchoolIdAndEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDateDesc(
						schoolId, employeeId, from, to)
				: staffAttendanceRecordRepository.findAllBySchoolIdAndEmployeeIdOrderByAttendanceDateDesc(schoolId, employeeId);

		Map<AttendanceStatus, Long> counts = records.stream()
				.collect(Collectors.groupingBy(StaffAttendanceRecord::getStatus, Collectors.counting()));

		return new EmployeeAttendanceHistoryResponse(
				employee.getId(),
				employee.getName(),
				from,
				to,
				records.size(),
				counts.getOrDefault(AttendanceStatus.PRESENT, 0L),
				counts.getOrDefault(AttendanceStatus.ABSENT, 0L),
				counts.getOrDefault(AttendanceStatus.LATE, 0L),
				counts.getOrDefault(AttendanceStatus.HALF_DAY, 0L),
				records.stream().map(StaffAttendanceRecordResponse::from).toList()
		);
	}

}
