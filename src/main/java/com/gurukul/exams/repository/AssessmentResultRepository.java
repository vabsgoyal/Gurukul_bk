package com.gurukul.exams.repository;

import com.gurukul.exams.entity.AssessmentResult;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssessmentResultRepository extends JpaRepository<AssessmentResult, UUID> {

	@EntityGraph(attributePaths = {"student"})
	List<AssessmentResult> findAllByAssessmentId(UUID assessmentId);

	Optional<AssessmentResult> findByAssessmentIdAndStudentId(UUID assessmentId, UUID studentId);

	@EntityGraph(attributePaths = {"assessment", "assessment.subject"})
	List<AssessmentResult> findAllBySchoolIdAndStudentIdAndAssessment_Section_IdAndAssessment_Term(
			UUID schoolId, UUID studentId, UUID sectionId, String term);

}
