package com.gurukul.teachers.repository;

import com.gurukul.teachers.entity.TeacherAssignmentRole;
import com.gurukul.teachers.entity.TeacherClassAssignment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeacherClassAssignmentRepository extends JpaRepository<TeacherClassAssignment, UUID> {

	@EntityGraph(attributePaths = {"teacher", "classSection"})
	List<TeacherClassAssignment> findAllBySchoolIdAndTeacherIdOrderBySubjectNameAsc(
			UUID schoolId, UUID teacherId);

	@EntityGraph(attributePaths = {"teacher", "classSection"})
	List<TeacherClassAssignment> findAllBySchoolIdAndClassSectionIdOrderBySubjectNameAsc(
			UUID schoolId, UUID classSectionId);

	@EntityGraph(attributePaths = {"teacher", "classSection"})
	Optional<TeacherClassAssignment> findByIdAndSchoolId(UUID id, UUID schoolId);

	boolean existsBySchoolIdAndTeacherIdAndClassSectionIdAndSubjectNameAndAssignmentRole(
			UUID schoolId,
			UUID teacherId,
			UUID classSectionId,
			String subjectName,
			TeacherAssignmentRole assignmentRole);

	long countBySchoolId(UUID schoolId);

	long countBySchoolIdAndAssignmentRole(UUID schoolId, TeacherAssignmentRole assignmentRole);

	long countBySchoolIdAndTeacherId(UUID schoolId, UUID teacherId);

	void deleteAllBySchoolIdAndTeacherId(UUID schoolId, UUID teacherId);

	@Query("select count(distinct assignment.classSection.id) from TeacherClassAssignment assignment where assignment.schoolId = :schoolId")
	long countDistinctClassSectionsBySchoolId(@Param("schoolId") UUID schoolId);

}
