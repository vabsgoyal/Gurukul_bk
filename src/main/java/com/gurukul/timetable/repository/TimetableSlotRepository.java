package com.gurukul.timetable.repository;

import com.gurukul.timetable.entity.TimetableSlot;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TimetableSlotRepository extends JpaRepository<TimetableSlot, UUID> {

	@EntityGraph(attributePaths = {"subject", "teacher", "section"})
	List<TimetableSlot> findAllBySchoolIdAndSectionId(UUID schoolId, UUID sectionId);

	@EntityGraph(attributePaths = {"subject", "teacher", "section"})
	List<TimetableSlot> findAllBySchoolIdAndTeacherIdAndAcademicYear(UUID schoolId, UUID teacherId, String academicYear);

	/** Other sections' slots (same school + year) held by any of these teachers - the clash candidates. */
	@EntityGraph(attributePaths = {"teacher", "section"})
	@Query("SELECT s FROM TimetableSlot s WHERE s.schoolId = :schoolId AND s.academicYear = :academicYear "
			+ "AND s.teacher.id IN :teacherIds AND s.section.id <> :sectionId")
	List<TimetableSlot> findClashCandidates(@Param("schoolId") UUID schoolId,
			@Param("academicYear") String academicYear,
			@Param("teacherIds") Collection<UUID> teacherIds,
			@Param("sectionId") UUID sectionId);

	@Query("SELECT MAX(s.academicYear) FROM TimetableSlot s WHERE s.schoolId = :schoolId AND s.teacher.id = :teacherId")
	String findLatestAcademicYearForTeacher(@Param("schoolId") UUID schoolId, @Param("teacherId") UUID teacherId);

	long countBySchoolIdAndDayOfWeek(UUID schoolId, DayOfWeek dayOfWeek);

	@Query("SELECT DISTINCT s.periodNumber FROM TimetableSlot s WHERE s.schoolId = :schoolId")
	List<Integer> findDistinctPeriodNumbersInUse(@Param("schoolId") UUID schoolId);

	@Modifying(flushAutomatically = true)
	@Query("DELETE FROM TimetableSlot s WHERE s.schoolId = :schoolId AND s.section.id = :sectionId")
	void deleteAllBySchoolIdAndSectionId(@Param("schoolId") UUID schoolId, @Param("sectionId") UUID sectionId);

}
