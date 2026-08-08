package com.gurukul.teachers.repository;

import com.gurukul.teachers.entity.TeacherStudentAttendance;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeacherStudentAttendanceRepository extends JpaRepository<TeacherStudentAttendance, UUID> {

	@EntityGraph(attributePaths = {"teacher", "classSection", "student"})
	List<TeacherStudentAttendance> findAllBySchoolIdAndClassSectionIdAndAttendanceDateAndSessionNameOrderByStudent_RollNumberAsc(
			UUID schoolId, UUID classSectionId, LocalDate attendanceDate, String sessionName);

	@EntityGraph(attributePaths = {"teacher", "classSection", "student"})
	List<TeacherStudentAttendance> findAllBySchoolIdAndStudentIdOrderByAttendanceDateDesc(
			UUID schoolId, UUID studentId);

	@EntityGraph(attributePaths = {"teacher", "classSection", "student"})
	Optional<TeacherStudentAttendance> findBySchoolIdAndClassSectionIdAndStudentIdAndAttendanceDateAndSessionName(
			UUID schoolId, UUID classSectionId, UUID studentId, LocalDate attendanceDate, String sessionName);

	void deleteAllBySchoolIdAndTeacherId(UUID schoolId, UUID teacherId);

	long countBySchoolId(UUID schoolId);

}
