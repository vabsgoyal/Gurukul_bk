package com.gurukul.exams.repository;

import com.gurukul.exams.entity.AssessmentResult;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssessmentResultRepository extends JpaRepository<AssessmentResult, UUID> {

	@EntityGraph(attributePaths = {"assessment", "assessment.section", "assessment.subject", "student"})
	List<AssessmentResult> findAllBySchoolIdAndAssessmentIdOrderByStudentRollNumberAsc(UUID schoolId, UUID assessmentId);

	@EntityGraph(attributePaths = {"assessment", "assessment.section", "assessment.subject", "student"})
	List<AssessmentResult> findAllBySchoolIdAndStudentIdOrderByAssessmentAssessmentDateDesc(UUID schoolId, UUID studentId);

	@EntityGraph(attributePaths = {"assessment", "assessment.section"})
	List<AssessmentResult> findAllBySchoolIdAndAssessmentCreatedByTeacherId(UUID schoolId, UUID employeeId);

	Optional<AssessmentResult> findByIdAndSchoolId(UUID id, UUID schoolId);

	Optional<AssessmentResult> findBySchoolIdAndAssessmentIdAndStudentId(UUID schoolId, UUID assessmentId, UUID studentId);

}
