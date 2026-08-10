package com.gurukul.exams.service;

import com.gurukul.academics.repository.SectionSubjectTeacherRepository;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthContext;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.exams.dto.AssessmentResultDtos.AssessmentResultsResponse;
import com.gurukul.exams.dto.AssessmentResultDtos.ResultEntry;
import com.gurukul.exams.dto.AssessmentResultDtos.StudentResultResponse;
import com.gurukul.exams.dto.AssessmentResultDtos.SubmitResultsRequest;
import com.gurukul.exams.entity.Assessment;
import com.gurukul.exams.entity.AssessmentResult;
import com.gurukul.exams.repository.AssessmentRepository;
import com.gurukul.exams.repository.AssessmentResultRepository;
import com.gurukul.exams.repository.ReportCardPublicationRepository;
import com.gurukul.students.entity.Student;
import com.gurukul.students.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssessmentResultService {

	private final AssessmentResultRepository assessmentResultRepository;
	private final AssessmentRepository assessmentRepository;
	private final StudentRepository studentRepository;
	private final SectionSubjectTeacherRepository sectionSubjectTeacherRepository;
	private final ReportCardPublicationRepository reportCardPublicationRepository;
	private final SchoolContext schoolContext;

	@Transactional
	public AssessmentResultsResponse submitResults(UUID assessmentId, SubmitResultsRequest request) {
		Assessment assessment = findScopedAssessment(assessmentId);
		requireCanManageResults(assessment);
		requireNotLocked(assessment);

		UUID schoolId = schoolContext.getSchoolId();
		for (ResultEntry entry : request.getResults()) {
			Student student = studentRepository.findByIdAndSchoolId(entry.getStudentId(), schoolId)
					.orElseThrow(() -> new EntityNotFoundException("Student not found"));
			if (!student.getClassSection().getId().equals(assessment.getSection().getId())) {
				throw new IllegalArgumentException("Student does not belong to this assessment's section");
			}

			AssessmentResult result = assessmentResultRepository
					.findByAssessmentIdAndStudentId(assessmentId, student.getId())
					.orElseGet(() -> {
						AssessmentResult r = new AssessmentResult();
						r.setSchoolId(schoolId);
						r.setAssessment(assessment);
						r.setStudent(student);
						return r;
					});
			result.setAbsent(entry.isAbsent());
			result.setMarksObtained(entry.isAbsent() ? null : entry.getMarksObtained());
			result.setRemarks(entry.getRemarks());
			assessmentResultRepository.save(result);
		}

		return getResultsForAssessment(assessmentId);
	}

	@Transactional(readOnly = true)
	public AssessmentResultsResponse getResultsForAssessment(UUID assessmentId) {
		Assessment assessment = findScopedAssessment(assessmentId);
		List<Student> roster = studentRepository
				.findAllBySchoolIdAndClassSectionId(schoolContext.getSchoolId(), assessment.getSection().getId());
		Map<UUID, AssessmentResult> byStudentId = assessmentResultRepository.findAllByAssessmentId(assessmentId).stream()
				.collect(Collectors.toMap(r -> r.getStudent().getId(), Function.identity()));

		List<StudentResultResponse> rows = roster.stream()
				.map(student -> {
					AssessmentResult result = byStudentId.get(student.getId());
					return new StudentResultResponse(
							student.getId(),
							student.getName(),
							student.getRollNumber(),
							result != null ? result.getMarksObtained() : null,
							result != null && result.isAbsent(),
							result != null ? result.getRemarks() : null
					);
				})
				.toList();

		return new AssessmentResultsResponse(assessment.getId(), assessment.getTitle(), assessment.getMaxMarks(), rows);
	}

	private void requireCanManageResults(Assessment assessment) {
		AuthPrincipal principal = AuthContext.current();
		if (principal.getRole() == Role.ADMIN) {
			return;
		}
		if (principal.getRole() != Role.TEACHER) {
			throw new AccessDeniedException("Only a teacher or admin may enter results");
		}
		boolean isCreator = assessment.getCreatedByTeacher() != null
				&& assessment.getCreatedByTeacher().getId().equals(principal.getOwnerId());
		boolean isSubjectTeacher = assessment.getSubject() != null
				&& sectionSubjectTeacherRepository.findBySectionIdAndSubjectIdAndTeacherId(
						assessment.getSection().getId(), assessment.getSubject().getId(), principal.getOwnerId()).isPresent();
		// A class teacher has full edit access across every subject in their own section - not
		// just their own subject - so they can correct/complete marks a subject teacher hasn't
		// gotten to yet before checking everything and publishing.
		boolean isClassTeacher = assessment.getSection().getClassTeacher() != null
				&& assessment.getSection().getClassTeacher().getId().equals(principal.getOwnerId());
		if (!isCreator && !isSubjectTeacher && !isClassTeacher) {
			throw new AccessDeniedException("You are not authorized to manage results for this assessment");
		}
	}

	private void requireNotLocked(Assessment assessment) {
		if (assessment.getTerm() == null) {
			return;
		}
		if (reportCardPublicationRepository.existsByClassSection_IdAndTerm(assessment.getSection().getId(), assessment.getTerm())) {
			throw new IllegalStateException(
					"Report cards for " + assessment.getTerm() + " have already been published - marks entry is locked");
		}
	}

	private Assessment findScopedAssessment(UUID assessmentId) {
		return assessmentRepository.findByIdAndSchoolId(assessmentId, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Assessment not found"));
	}

}
