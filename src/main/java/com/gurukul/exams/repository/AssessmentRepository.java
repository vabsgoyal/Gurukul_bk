package com.gurukul.exams.repository;

import com.gurukul.exams.entity.Assessment;
import com.gurukul.exams.entity.AssessmentType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

	@EntityGraph(attributePaths = {"section", "subject", "createdByTeacher"})
	List<Assessment> findAllBySchoolIdAndSectionIdOrderByAssessmentDateDesc(UUID schoolId, UUID sectionId);

	@EntityGraph(attributePaths = {"section", "subject", "createdByTeacher"})
	List<Assessment> findAllBySchoolIdAndSectionIdAndTypeOrderByAssessmentDateDesc(
			UUID schoolId, UUID sectionId, AssessmentType type);

	@EntityGraph(attributePaths = {"section", "subject", "createdByTeacher"})
	Optional<Assessment> findByIdAndSchoolId(UUID id, UUID schoolId);

	@Query("select distinct a.term from Assessment a "
			+ "where a.schoolId = :schoolId and a.section.id = :sectionId and a.term is not null order by a.term")
	List<String> findDistinctTermsBySchoolIdAndSectionId(@Param("schoolId") UUID schoolId, @Param("sectionId") UUID sectionId);

	List<Assessment> findAllBySchoolIdAndSectionIdAndTermIsNull(UUID schoolId, UUID sectionId);

}
