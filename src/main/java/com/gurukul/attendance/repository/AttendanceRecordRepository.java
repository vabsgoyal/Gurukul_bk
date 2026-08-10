package com.gurukul.attendance.repository;

import com.gurukul.attendance.entity.AttendanceRecord;
import com.gurukul.attendance.entity.AttendanceStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {

	/** Projection for status roll-ups where only the student id and status are needed - avoids the
	 * student/section/markedByTeacher entity-graph join used by the full-fetch queries below. */
	interface StudentStatusView {
		UUID getStudentId();
		AttendanceStatus getStatus();
	}

	@Query("select ar.student.id as studentId, ar.status as status from AttendanceRecord ar "
			+ "where ar.schoolId = :schoolId and ar.section.id = :sectionId")
	List<StudentStatusView> findStudentStatusesBySchoolIdAndSectionId(
			@Param("schoolId") UUID schoolId, @Param("sectionId") UUID sectionId);

	@Query("select ar.student.id as studentId, ar.status as status from AttendanceRecord ar "
			+ "where ar.schoolId = :schoolId and ar.section.id = :sectionId "
			+ "and ar.attendanceDate between :from and :to")
	List<StudentStatusView> findStudentStatusesBySchoolIdAndSectionIdAndAttendanceDateBetween(
			@Param("schoolId") UUID schoolId, @Param("sectionId") UUID sectionId,
			@Param("from") LocalDate from, @Param("to") LocalDate to);

	@EntityGraph(attributePaths = {"student", "section", "markedByTeacher"})
	List<AttendanceRecord> findAllBySchoolIdAndSectionIdAndAttendanceDate(UUID schoolId, UUID sectionId, LocalDate attendanceDate);

	Optional<AttendanceRecord> findBySchoolIdAndStudentIdAndAttendanceDate(UUID schoolId, UUID studentId, LocalDate attendanceDate);

	@EntityGraph(attributePaths = {"student", "section", "markedByTeacher"})
	List<AttendanceRecord> findAllBySchoolIdAndStudentIdOrderByAttendanceDateDesc(UUID schoolId, UUID studentId);

	@EntityGraph(attributePaths = {"student", "section", "markedByTeacher"})
	List<AttendanceRecord> findAllBySchoolIdAndStudentIdAndAttendanceDateBetweenOrderByAttendanceDateDesc(
			UUID schoolId, UUID studentId, LocalDate from, LocalDate to);

}
