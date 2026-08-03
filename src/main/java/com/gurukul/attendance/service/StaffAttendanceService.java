package com.gurukul.attendance.service;

import com.gurukul.attendance.dto.StaffAttendanceDtos.BulkStaffAttendanceRequest;
import com.gurukul.attendance.dto.StaffAttendanceDtos.EmployeeAttendanceHistoryResponse;
import com.gurukul.attendance.dto.StaffAttendanceDtos.StaffAttendanceEntryRequest;
import com.gurukul.attendance.dto.StaffAttendanceDtos.StaffAttendanceEntryResponse;
import com.gurukul.attendance.dto.StaffAttendanceDtos.StaffAttendanceRecordResponse;
import com.gurukul.attendance.dto.StaffAttendanceDtos.StaffAttendanceRosterResponse;
import com.gurukul.attendance.entity.AttendanceStatus;
import com.gurukul.attendance.entity.StaffAttendanceRecord;
import com.gurukul.attendance.repository.StaffAttendanceRecordRepository;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthContext;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.employees.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
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
			staffAttendanceRecordRepository.save(record);
		}

		return getStaffRoster(request.getDate());
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
							record != null ? record.getRemarks() : null
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
