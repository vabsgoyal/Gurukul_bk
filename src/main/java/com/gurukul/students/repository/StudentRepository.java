package com.gurukul.students.repository;

import com.gurukul.students.entity.Student;
import com.gurukul.students.entity.StudentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, UUID> {

	@EntityGraph(attributePaths = {"classSection", "classSection.classTeacher"})
	List<Student> findAllBySchoolId(UUID schoolId);

	/** Slice, not Page: avoids Spring Data's automatic separate COUNT(*) query on every page - over a
	 *  cross-region DB link that's a full extra round-trip per request. Total count is fetched
	 *  separately (see StudentService.list) only on page 0. */
	@EntityGraph(attributePaths = {"classSection", "classSection.classTeacher"})
	Slice<Student> findAllBySchoolIdOrderByNameAsc(UUID schoolId, Pageable pageable);

	@EntityGraph(attributePaths = {"classSection", "classSection.classTeacher"})
	List<Student> findAllBySchoolIdAndClassSectionId(UUID schoolId, UUID classSectionId);

	@EntityGraph(attributePaths = {"classSection", "classSection.classTeacher"})
	List<Student> findAllBySchoolIdAndClassSection_ClassNameAndClassSection_SectionAndClassSection_AcademicYear(
			UUID schoolId, String className, String section, String academicYear);

	@EntityGraph(attributePaths = {"classSection", "classSection.classTeacher"})
	List<Student> findAllBySchoolIdAndClassSection_ClassName(UUID schoolId, String className);

	@EntityGraph(attributePaths = {"classSection", "classSection.classTeacher"})
	Optional<Student> findByIdAndSchoolId(UUID id, UUID schoolId);

	List<Student> findAllBySchoolIdAndParentContact(UUID schoolId, String parentContact);

	Optional<Student> findBySchoolIdAndRegistrationNumber(UUID schoolId, String registrationNumber);

	long countBySchoolId(UUID schoolId);

	/** Used to recompute server-assigned roll numbers (alphabetical rank within the active roster) whenever the roster changes. */
	List<Student> findAllByClassSectionIdAndStatus(UUID classSectionId, StudentStatus status);

}
