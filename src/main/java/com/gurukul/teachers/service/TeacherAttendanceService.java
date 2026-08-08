package com.gurukul.teachers.service;

import com.gurukul.common.SchoolContext;
import com.gurukul.students.entity.ClassSection;
import com.gurukul.students.entity.Student;
import com.gurukul.students.repository.StudentRepository;
import com.gurukul.students.service.ClassSectionService;
import com.gurukul.teachers.dto.MarkStudentAttendanceRequest;
import com.gurukul.teachers.dto.StudentAttendanceEntryRequest;
import com.gurukul.teachers.dto.StudentAttendanceRecordResponse;
import com.gurukul.teachers.dto.StudentAttendanceSummaryResponse;
import com.gurukul.teachers.entity.StudentAttendanceStatus;
import com.gurukul.teachers.entity.Teacher;
import com.gurukul.teachers.entity.TeacherStudentAttendance;
import com.gurukul.teachers.repository.TeacherStudentAttendanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeacherAttendanceService {

	private final TeacherStudentAttendanceRepository attendanceRepository;
	private final TeacherService teacherService;
	private final StudentRepository studentRepository;
	private final ClassSectionService classSectionService;
	private final SchoolContext schoolContext;

	@Transactional
	public StudentAttendanceSummaryResponse markAttendance(UUID teacherId, MarkStudentAttendanceRequest request) {
		UUID schoolId = schoolContext.getSchoolId();
		Teacher teacher = teacherService.getScopedTeacher(teacherId);
		ClassSection classSection = classSectionService.getScopedClassSection(request.getClassSectionId());

		for (StudentAttendanceEntryRequest entry : request.getEntries()) {
			Student student = getStudentInClassSection(schoolId, entry.getStudentId(), classSection.getId());
			TeacherStudentAttendance attendance = attendanceRepository
					.findBySchoolIdAndClassSectionIdAndStudentIdAndAttendanceDateAndSessionName(
							schoolId,
							classSection.getId(),
							student.getId(),
							request.getAttendanceDate(),
							request.getSessionName())
					.orElseGet(TeacherStudentAttendance::new);

			attendance.setSchoolId(schoolId);
			attendance.setTeacher(teacher);
			attendance.setClassSection(classSection);
			attendance.setStudent(student);
			attendance.setAttendanceDate(request.getAttendanceDate());
			attendance.setSessionName(request.getSessionName());
			attendance.setStatus(entry.getStatus());
			attendance.setRemarks(entry.getRemarks());
			attendanceRepository.save(attendance);
		}

		return getClassSectionAttendance(
				classSection.getId(),
				request.getAttendanceDate(),
				request.getSessionName());
	}

	public StudentAttendanceSummaryResponse getClassSectionAttendance(
			UUID classSectionId,
			LocalDate attendanceDate,
			String sessionName) {
		ClassSection classSection = classSectionService.getScopedClassSection(classSectionId);
		List<StudentAttendanceRecordResponse> records = attendanceRepository
				.findAllBySchoolIdAndClassSectionIdAndAttendanceDateAndSessionNameOrderByStudent_RollNumberAsc(
						schoolContext.getSchoolId(),
						classSectionId,
						attendanceDate,
						sessionName)
				.stream()
				.map(StudentAttendanceRecordResponse::from)
				.toList();

		Teacher teacher = records.isEmpty() ? null : teacherService.getScopedTeacher(records.get(0).getTeacherId());
		return new StudentAttendanceSummaryResponse(
				schoolContext.getSchoolId(),
				classSection.getId(),
				classSection.getDisplayLabel(),
				teacher == null ? null : teacher.getId(),
				teacher == null ? null : teacher.getName(),
				attendanceDate,
				sessionName,
				countStatus(records, StudentAttendanceStatus.PRESENT),
				countStatus(records, StudentAttendanceStatus.ABSENT),
				countStatus(records, StudentAttendanceStatus.LATE),
				countStatus(records, StudentAttendanceStatus.EXCUSED),
				records
		);
	}

	public List<StudentAttendanceRecordResponse> getStudentAttendance(UUID studentId) {
		studentRepository.findByIdAndSchoolId(studentId, schoolContext.getSchoolId())
				.orElseThrow(() -> new IllegalArgumentException("Student not found for this school"));
		return attendanceRepository.findAllBySchoolIdAndStudentIdOrderByAttendanceDateDesc(
						schoolContext.getSchoolId(), studentId)
				.stream()
				.map(StudentAttendanceRecordResponse::from)
				.toList();
	}

	private Student getStudentInClassSection(UUID schoolId, UUID studentId, UUID classSectionId) {
		Student student = studentRepository.findByIdAndSchoolId(studentId, schoolId)
				.orElseThrow(() -> new IllegalArgumentException("Student not found for this school"));
		if (!student.getClassSection().getId().equals(classSectionId)) {
			throw new IllegalArgumentException("Student does not belong to this class-section");
		}
		return student;
	}

	private long countStatus(List<StudentAttendanceRecordResponse> records, StudentAttendanceStatus status) {
		return records.stream()
				.filter(record -> record.getStatus() == status)
				.count();
	}

}
