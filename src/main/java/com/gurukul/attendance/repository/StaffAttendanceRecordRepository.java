package com.gurukul.attendance.repository;

import com.gurukul.attendance.entity.StaffAttendanceRecord;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffAttendanceRecordRepository extends JpaRepository<StaffAttendanceRecord, UUID> {

	@EntityGraph(attributePaths = {"employee", "markedByEmployee"})
	List<StaffAttendanceRecord> findAllBySchoolIdAndAttendanceDate(UUID schoolId, LocalDate attendanceDate);

	Optional<StaffAttendanceRecord> findBySchoolIdAndEmployeeIdAndAttendanceDate(
			UUID schoolId, UUID employeeId, LocalDate attendanceDate);

	@EntityGraph(attributePaths = {"employee", "markedByEmployee"})
	List<StaffAttendanceRecord> findAllBySchoolIdAndEmployeeIdOrderByAttendanceDateDesc(UUID schoolId, UUID employeeId);

	@EntityGraph(attributePaths = {"employee", "markedByEmployee"})
	List<StaffAttendanceRecord> findAllBySchoolIdAndEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDateDesc(
			UUID schoolId, UUID employeeId, LocalDate from, LocalDate to);

}
