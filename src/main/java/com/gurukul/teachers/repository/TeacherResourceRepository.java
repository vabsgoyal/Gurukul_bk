package com.gurukul.teachers.repository;

import com.gurukul.teachers.entity.TeacherResource;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeacherResourceRepository extends JpaRepository<TeacherResource, UUID> {

	@EntityGraph(attributePaths = {"teacher", "classSection"})
	List<TeacherResource> findAllBySchoolIdAndTeacherIdOrderByCreatedAtDesc(UUID schoolId, UUID teacherId);

	@EntityGraph(attributePaths = {"teacher", "classSection"})
	List<TeacherResource> findAllBySchoolIdAndClassSectionIdOrderByCreatedAtDesc(UUID schoolId, UUID classSectionId);

	@EntityGraph(attributePaths = {"teacher", "classSection"})
	Optional<TeacherResource> findByIdAndSchoolId(UUID id, UUID schoolId);

	void deleteAllBySchoolIdAndTeacherId(UUID schoolId, UUID teacherId);

	long countBySchoolId(UUID schoolId);

}
