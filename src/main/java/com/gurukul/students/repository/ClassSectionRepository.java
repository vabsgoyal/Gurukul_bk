package com.gurukul.students.repository;

import com.gurukul.students.entity.ClassSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassSectionRepository extends JpaRepository<ClassSection, UUID> {

	List<ClassSection> findAllBySchoolIdOrderByClassNameAscSectionAsc(UUID schoolId);

	Optional<ClassSection> findByIdAndSchoolId(UUID id, UUID schoolId);

	Optional<ClassSection> findBySchoolIdAndClassNameAndSectionAndAcademicYear(
			UUID schoolId, String className, String section, String academicYear);

	boolean existsBySchoolIdAndClassNameAndSectionAndAcademicYear(
			UUID schoolId, String className, String section, String academicYear);

	boolean existsBySchoolIdAndClassTeacherIdAndAcademicYear(UUID schoolId, UUID teacherId, String academicYear);

	long countBySchoolId(UUID schoolId);

}
