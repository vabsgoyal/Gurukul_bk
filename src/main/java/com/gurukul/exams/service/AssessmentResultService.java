package com.gurukul.exams.service;

import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.exams.dto.AssessmentResultResponse;
import com.gurukul.exams.dto.BulkAssessmentResultRequest;
import com.gurukul.exams.entity.Assessment;
import com.gurukul.exams.entity.AssessmentResult;
import com.gurukul.exams.repository.AssessmentResultRepository;
import com.gurukul.students.entity.Student;
import com.gurukul.students.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssessmentResultService {

	private final AssessmentResultRepository assessmentResultRepository;
	private final AssessmentService assessmentService;
	private final StudentService studentService;
	private final SchoolContext schoolContext;

	public List<AssessmentResultResponse> listByAssessment(UUID assessmentId) {
		UUID schoolId = schoolContext.getSchoolId();
		assessmentService.getScopedEntity(assessmentId);
		return assessmentResultRepository.findAllBySchoolIdAndAssessmentIdOrderByStudentRollNumberAsc(schoolId, assessmentId)
				.stream().map(AssessmentResultResponse::from).toList();
	}

	@Transactional
	public List<AssessmentResultResponse> recordResults(UUID assessmentId, BulkAssessmentResultRequest request) {
		UUID schoolId = schoolContext.getSchoolId();
		Assessment assessment = assessmentService.getScopedEntity(assessmentId);

		return request.getResults().stream().map(entry -> {
			if (entry.getMarksObtained().compareTo(assessment.getMaxMarks()) > 0) {
				throw new IllegalArgumentException(
						"Marks obtained (" + entry.getMarksObtained() + ") cannot exceed max marks (" + assessment.getMaxMarks() + ")");
			}
			Student student = studentService.getScopedEntity(entry.getStudentId());

			AssessmentResult result = assessmentResultRepository
					.findBySchoolIdAndAssessmentIdAndStudentId(schoolId, assessmentId, student.getId())
					.orElseGet(() -> {
						AssessmentResult created = new AssessmentResult();
						created.setSchoolId(schoolId);
						created.setAssessment(assessment);
						created.setStudent(student);
						return created;
					});

			result.setMarksObtained(entry.getMarksObtained());
			result.setRemarks(entry.getRemarks());
			return AssessmentResultResponse.from(assessmentResultRepository.save(result));
		}).toList();
	}

	@Transactional
	public AssessmentResultResponse update(UUID assessmentId, UUID resultId, BulkAssessmentResultRequest.Entry entry) {
		AssessmentResult result = findScoped(resultId);
		if (!result.getAssessment().getId().equals(assessmentId)) {
			throw new EntityNotFoundException("Result not found for this assessment");
		}
		if (entry.getMarksObtained().compareTo(result.getAssessment().getMaxMarks()) > 0) {
			throw new IllegalArgumentException(
					"Marks obtained (" + entry.getMarksObtained() + ") cannot exceed max marks (" + result.getAssessment().getMaxMarks() + ")");
		}
		result.setMarksObtained(entry.getMarksObtained());
		result.setRemarks(entry.getRemarks());
		return AssessmentResultResponse.from(assessmentResultRepository.save(result));
	}

	@Transactional
	public void delete(UUID assessmentId, UUID resultId) {
		AssessmentResult result = findScoped(resultId);
		if (!result.getAssessment().getId().equals(assessmentId)) {
			throw new EntityNotFoundException("Result not found for this assessment");
		}
		assessmentResultRepository.delete(result);
	}

	private AssessmentResult findScoped(UUID id) {
		return assessmentResultRepository.findByIdAndSchoolId(id, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Assessment result not found"));
	}

}
