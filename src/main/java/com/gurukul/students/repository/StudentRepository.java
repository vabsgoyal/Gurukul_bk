package com.gurukul.students.repository;

import com.gurukul.students.entity.Student;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, UUID> {

	@EntityGraph(attributePaths = "classSection")
	List<Student> findAllBySchoolId(UUID schoolId);

	@EntityGraph(attributePaths = "classSection")
	List<Student> findAllBySchoolIdAndClassSectionId(UUID schoolId, UUID classSectionId);

	@EntityGraph(attributePaths = "classSection")
	List<Student> findAllBySchoolIdAndClassSection_ClassNameAndClassSection_SectionAndClassSection_AcademicYear(
			UUID schoolId, String className, String section, String academicYear);

	@EntityGraph(attributePaths = "classSection")
	Optional<Student> findByIdAndSchoolId(UUID id, UUID schoolId);

	boolean existsBySchoolIdAndRollNumber(UUID schoolId, String rollNumber);

	boolean existsBySchoolIdAndRollNumberAndIdNot(UUID schoolId, String rollNumber, UUID id);

	long countBySchoolId(UUID schoolId);

}
