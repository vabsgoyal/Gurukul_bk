package com.gurukul.students.repository;

import com.gurukul.students.entity.Student;
import com.gurukul.students.entity.StudentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, UUID> {

	@EntityGraph(attributePaths = "classSection")
	List<Student> findAllBySchoolId(UUID schoolId);

	@EntityGraph(attributePaths = "classSection")
	Page<Student> findAllBySchoolIdOrderByNameAsc(UUID schoolId, Pageable pageable);

	@EntityGraph(attributePaths = "classSection")
	List<Student> findAllBySchoolIdAndClassSectionId(UUID schoolId, UUID classSectionId);

	@EntityGraph(attributePaths = "classSection")
	List<Student> findAllBySchoolIdAndClassSection_ClassNameAndClassSection_SectionAndClassSection_AcademicYear(
			UUID schoolId, String className, String section, String academicYear);

	@EntityGraph(attributePaths = "classSection")
	List<Student> findAllBySchoolIdAndClassSection_ClassName(UUID schoolId, String className);

	@EntityGraph(attributePaths = "classSection")
	Optional<Student> findByIdAndSchoolId(UUID id, UUID schoolId);

	List<Student> findAllBySchoolIdAndParentContact(UUID schoolId, String parentContact);

	Optional<Student> findBySchoolIdAndRegistrationNumber(UUID schoolId, String registrationNumber);

	long countBySchoolId(UUID schoolId);

	/** Used to recompute server-assigned roll numbers (alphabetical rank within the active roster) whenever the roster changes. */
	List<Student> findAllByClassSectionIdAndStatus(UUID classSectionId, StudentStatus status);

}
