package com.gurukul.attendance.repository;

import com.gurukul.attendance.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {

	@EntityGraph(attributePaths = {"student", "section", "markedByTeacher"})
	List<AttendanceRecord> findAllBySchoolIdAndSectionIdAndAttendanceDate(UUID schoolId, UUID sectionId, LocalDate attendanceDate);

	Optional<AttendanceRecord> findBySchoolIdAndStudentIdAndAttendanceDate(UUID schoolId, UUID studentId, LocalDate attendanceDate);

	@EntityGraph(attributePaths = {"student", "section", "markedByTeacher"})
	List<AttendanceRecord> findAllBySchoolIdAndStudentIdOrderByAttendanceDateDesc(UUID schoolId, UUID studentId);

	@EntityGraph(attributePaths = {"student", "section", "markedByTeacher"})
	List<AttendanceRecord> findAllBySchoolIdAndStudentIdAndAttendanceDateBetweenOrderByAttendanceDateDesc(
			UUID schoolId, UUID studentId, LocalDate from, LocalDate to);

	@EntityGraph(attributePaths = {"student", "section", "markedByTeacher"})
	List<AttendanceRecord> findAllBySchoolIdAndSectionId(UUID schoolId, UUID sectionId);

	@EntityGraph(attributePaths = {"student", "section", "markedByTeacher"})
	List<AttendanceRecord> findAllBySchoolIdAndSectionIdAndAttendanceDateBetween(
			UUID schoolId, UUID sectionId, LocalDate from, LocalDate to);

}
