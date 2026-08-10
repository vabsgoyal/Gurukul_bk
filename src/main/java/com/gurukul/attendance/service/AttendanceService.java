package com.gurukul.attendance.service;

import com.gurukul.attendance.dto.AttendanceDtos.AttendanceEntryRequest;
import com.gurukul.attendance.dto.AttendanceDtos.AttendanceRecordResponse;
import com.gurukul.attendance.dto.AttendanceDtos.BulkAttendanceRequest;
import com.gurukul.attendance.dto.AttendanceDtos.SectionAttendanceHistoryResponse;
import com.gurukul.attendance.dto.AttendanceDtos.SectionAttendanceResponse;
import com.gurukul.attendance.dto.AttendanceDtos.SectionStudentAttendanceSummary;
import com.gurukul.attendance.dto.AttendanceDtos.StudentAttendanceEntryResponse;
import com.gurukul.attendance.dto.AttendanceDtos.StudentAttendanceHistoryResponse;
import com.gurukul.attendance.entity.AttendanceRecord;
import com.gurukul.attendance.entity.AttendanceStatus;
import com.gurukul.attendance.repository.AttendanceRecordRepository;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthContext;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.service.EmployeeService;
import com.gurukul.gamification.service.GamificationService;
import com.gurukul.parents.service.ParentService;
import com.gurukul.students.entity.ClassSection;
import com.gurukul.students.entity.Student;
import com.gurukul.students.repository.StudentRepository;
import com.gurukul.students.service.ClassSectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {

	private final AttendanceRecordRepository attendanceRecordRepository;
	private final StudentRepository studentRepository;
	private final ClassSectionService classSectionService;
	private final EmployeeService employeeService;
	private final ParentService parentService;
	private final SchoolContext schoolContext;
	private final GamificationService gamificationService;

	@Transactional
	public SectionAttendanceResponse markSection(UUID sectionId, BulkAttendanceRequest request) {
		UUID schoolId = schoolContext.getSchoolId();
		ClassSection section = classSectionService.getScopedClassSection(sectionId);
		Employee teacher = resolveMarkingTeacher(section, request.getTeacherId());

		for (AttendanceEntryRequest entry : request.getRecords()) {
			Student student = studentRepository.findByIdAndSchoolId(entry.getStudentId(), schoolId)
					.orElseThrow(() -> new EntityNotFoundException("Student not found"));
			if (!student.getClassSection().getId().equals(sectionId)) {
				throw new IllegalArgumentException("Student does not belong to this section");
			}

			AttendanceRecord record = attendanceRecordRepository
					.findBySchoolIdAndStudentIdAndAttendanceDate(schoolId, student.getId(), request.getDate())
					.orElseGet(() -> {
						AttendanceRecord newRecord = new AttendanceRecord();
						newRecord.setSchoolId(schoolId);
						newRecord.setStudent(student);
						newRecord.setSection(section);
						newRecord.setAttendanceDate(request.getDate());
						return newRecord;
					});
			record.setStatus(entry.getStatus());
			record.setMarkedByTeacher(teacher);
			record.setRemarks(entry.getRemarks());
			attendanceRecordRepository.save(record);
			gamificationService.recordAttendanceXp(schoolId, student.getId(), request.getDate(), entry.getStatus());
		}

		return getSectionRoster(sectionId, request.getDate());
	}

	@Transactional(readOnly = true)
	public SectionAttendanceResponse getSectionRoster(UUID sectionId, LocalDate date) {
		UUID schoolId = schoolContext.getSchoolId();
		ClassSection section = classSectionService.getScopedClassSection(sectionId);

		List<Student> students = studentRepository.findAllBySchoolIdAndClassSectionId(schoolId, sectionId);
		Map<UUID, AttendanceRecord> recordsByStudentId = attendanceRecordRepository
				.findAllBySchoolIdAndSectionIdAndAttendanceDate(schoolId, sectionId, date).stream()
				.collect(Collectors.toMap(r -> r.getStudent().getId(), Function.identity()));

		List<StudentAttendanceEntryResponse> entries = students.stream()
				.map(student -> {
					AttendanceRecord record = recordsByStudentId.get(student.getId());
					return new StudentAttendanceEntryResponse(
							student.getId(),
							student.getRollNumber(),
							student.getName(),
							record != null ? record.getStatus() : null,
							record != null ? record.getRemarks() : null
					);
				})
				.toList();

		return new SectionAttendanceResponse(
				section.getId(), section.getClassName(), section.getSection(), section.getAcademicYear(), date, entries);
	}

	@Transactional(readOnly = true)
	public StudentAttendanceHistoryResponse getStudentHistory(UUID studentId, LocalDate from, LocalDate to) {
		AuthPrincipal principal = AuthContext.current();
		if (principal.getRole() == Role.STUDENT && !principal.getOwnerId().equals(studentId)) {
			throw new AccessDeniedException("Students can only view their own attendance");
		}
		if (principal.getRole() == Role.PARENT) {
			parentService.requireLinkedChild(principal.getOwnerId(), studentId, principal.getSchoolId());
		}

		UUID schoolId = schoolContext.getSchoolId();
		Student student = studentRepository.findByIdAndSchoolId(studentId, schoolId)
				.orElseThrow(() -> new EntityNotFoundException("Student not found"));

		List<AttendanceRecord> records = (from != null && to != null)
				? attendanceRecordRepository.findAllBySchoolIdAndStudentIdAndAttendanceDateBetweenOrderByAttendanceDateDesc(
						schoolId, studentId, from, to)
				: attendanceRecordRepository.findAllBySchoolIdAndStudentIdOrderByAttendanceDateDesc(schoolId, studentId);

		Map<AttendanceStatus, Long> counts = records.stream()
				.collect(Collectors.groupingBy(AttendanceRecord::getStatus, Collectors.counting()));

		return new StudentAttendanceHistoryResponse(
				student.getId(),
				student.getName(),
				student.getRollNumber(),
				from,
				to,
				records.size(),
				counts.getOrDefault(AttendanceStatus.PRESENT, 0L),
				counts.getOrDefault(AttendanceStatus.ABSENT, 0L),
				counts.getOrDefault(AttendanceStatus.LATE, 0L),
				counts.getOrDefault(AttendanceStatus.HALF_DAY, 0L),
				records.stream().map(AttendanceRecordResponse::from).toList()
		);
	}

	@Transactional(readOnly = true)
	public SectionAttendanceHistoryResponse getSectionHistory(UUID sectionId, LocalDate from, LocalDate to) {
		UUID schoolId = schoolContext.getSchoolId();
		ClassSection section = classSectionService.getScopedClassSection(sectionId);

		List<Student> students = studentRepository.findAllBySchoolIdAndClassSectionId(schoolId, sectionId).stream()
				.sorted(Comparator.comparing(Student::getRollNumber))
				.toList();

		List<AttendanceRecordRepository.StudentStatusView> records = (from != null && to != null)
				? attendanceRecordRepository.findStudentStatusesBySchoolIdAndSectionIdAndAttendanceDateBetween(schoolId, sectionId, from, to)
				: attendanceRecordRepository.findStudentStatusesBySchoolIdAndSectionId(schoolId, sectionId);

		Map<UUID, List<AttendanceRecordRepository.StudentStatusView>> recordsByStudentId = records.stream()
				.collect(Collectors.groupingBy(AttendanceRecordRepository.StudentStatusView::getStudentId));

		List<SectionStudentAttendanceSummary> summaries = students.stream()
				.map(student -> {
					List<AttendanceRecordRepository.StudentStatusView> studentRecords = recordsByStudentId.getOrDefault(student.getId(), List.of());
					Map<AttendanceStatus, Long> counts = studentRecords.stream()
							.collect(Collectors.groupingBy(AttendanceRecordRepository.StudentStatusView::getStatus, Collectors.counting()));
					return new SectionStudentAttendanceSummary(
							student.getId(),
							student.getName(),
							student.getRollNumber(),
							studentRecords.size(),
							counts.getOrDefault(AttendanceStatus.PRESENT, 0L),
							counts.getOrDefault(AttendanceStatus.ABSENT, 0L),
							counts.getOrDefault(AttendanceStatus.LATE, 0L),
							counts.getOrDefault(AttendanceStatus.HALF_DAY, 0L)
					);
				})
				.toList();

		return new SectionAttendanceHistoryResponse(
				section.getId(), section.getClassName(), section.getSection(), section.getAcademicYear(), from, to, summaries);
	}

	private Employee resolveMarkingTeacher(ClassSection section, UUID requestedTeacherId) {
		AuthPrincipal principal = AuthContext.current();
		if (principal.getRole() == Role.ADMIN) {
			UUID teacherId = requestedTeacherId != null ? requestedTeacherId : principal.getOwnerId();
			return employeeService.getScopedEntity(teacherId);
		}
		// Caller is a TEACHER (the only other role permitted to hit this endpoint) - must be this
		// section's own class teacher; the requested teacherId in the body, if any, is ignored.
		if (section.getClassTeacher() == null || !section.getClassTeacher().getId().equals(principal.getOwnerId())) {
			throw new AccessDeniedException("Only this section's class teacher can mark its attendance");
		}
		return employeeService.getScopedEntity(principal.getOwnerId());
	}

}
