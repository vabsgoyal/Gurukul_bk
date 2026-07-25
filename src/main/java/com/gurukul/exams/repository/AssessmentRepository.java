package com.gurukul.exams.repository;

import com.gurukul.exams.entity.Assessment;
import com.gurukul.exams.entity.AssessmentType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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

}
