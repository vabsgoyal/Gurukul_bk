package com.gurukul.students.repository;

import com.gurukul.students.entity.ClassSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassSectionRepository extends JpaRepository<ClassSection, UUID> {

	List<ClassSection> findAllBySchoolIdOrderByClassNameAscSectionAsc(UUID schoolId);

	List<ClassSection> findAllBySchoolIdAndClassNameOrderBySectionAsc(UUID schoolId, String className);

	@Query("SELECT DISTINCT c.className FROM ClassSection c WHERE c.schoolId = :schoolId ORDER BY c.className ASC")
	List<String> findDistinctClassNamesBySchoolId(@Param("schoolId") UUID schoolId);

	Optional<ClassSection> findByIdAndSchoolId(UUID id, UUID schoolId);

	Optional<ClassSection> findBySchoolIdAndClassNameAndSectionAndAcademicYear(
			UUID schoolId, String className, String section, String academicYear);

	boolean existsBySchoolIdAndClassNameAndSectionAndAcademicYear(
			UUID schoolId, String className, String section, String academicYear);

	boolean existsBySchoolIdAndClassTeacherIdAndAcademicYear(UUID schoolId, UUID teacherId, String academicYear);

	boolean existsBySchoolIdAndClassTeacherIdAndAcademicYearAndIdNot(
			UUID schoolId, UUID teacherId, String academicYear, UUID id);

	List<ClassSection> findAllBySchoolIdAndClassTeacherIdOrderByAcademicYearDesc(UUID schoolId, UUID classTeacherId);

	long countBySchoolId(UUID schoolId);

}
