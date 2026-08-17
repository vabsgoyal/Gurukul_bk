package com.gurukul.exams.service;

import com.gurukul.academics.entity.Subject;
import com.gurukul.academics.repository.SubjectRepository;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthContext;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.service.EmployeeService;
import com.gurukul.exams.dto.AssessmentRequest;
import com.gurukul.exams.dto.AssessmentResponse;
import com.gurukul.exams.dto.AssessmentTermDtos.BackfillTermResponse;
import com.gurukul.exams.dto.AssessmentTermDtos.TermSummaryResponse;
import com.gurukul.exams.entity.Assessment;
import com.gurukul.exams.entity.AssessmentType;
import com.gurukul.exams.repository.AssessmentRepository;
import com.gurukul.exams.repository.ReportCardPublicationRepository;
import com.gurukul.students.entity.ClassSection;
import com.gurukul.students.service.ClassSectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssessmentService {

	private final AssessmentRepository assessmentRepository;
	private final SubjectRepository subjectRepository;
	private final ClassSectionService classSectionService;
	private final EmployeeService employeeService;
	private final SchoolContext schoolContext;
	private final ReportCardPublicationRepository reportCardPublicationRepository;

	@Transactional(readOnly = true)
	public List<AssessmentResponse> list(UUID sectionId, AssessmentType type) {
		classSectionService.getScopedClassSection(sectionId);
		UUID schoolId = schoolContext.getSchoolId();
		List<Assessment> assessments = type != null
				? assessmentRepository.findAllBySchoolIdAndSectionIdAndTypeOrderByAssessmentDateDesc(schoolId, sectionId, type)
				: assessmentRepository.findAllBySchoolIdAndSectionIdOrderByAssessmentDateDesc(schoolId, sectionId);
		return assessments.stream().map(AssessmentResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public AssessmentResponse getById(UUID id) {
		return AssessmentResponse.from(findScoped(id));
	}

	@Transactional
	public AssessmentResponse create(UUID sectionId, AssessmentRequest request) {
		ClassSection section = classSectionService.getScopedClassSection(sectionId);
		Assessment assessment = new Assessment();
		assessment.setSchoolId(schoolContext.getSchoolId());
		assessment.setSection(section);
		applyRequest(assessment, request);
		return AssessmentResponse.from(assessmentRepository.save(assessment));
	}

	@Transactional
	public AssessmentResponse update(UUID id, AssessmentRequest request) {
		Assessment assessment = findScoped(id);
		applyRequest(assessment, request);
		return AssessmentResponse.from(assessmentRepository.save(assessment));
	}

	@Transactional
	public void delete(UUID id) {
		assessmentRepository.delete(findScoped(id));
	}

	/**
	 * Every distinct term already used in this section, plus whether each has been published -
	 * backs a tap-to-select picker on the publish screen instead of blind free-text, so a teacher
	 * doesn't accidentally publish under a string that doesn't match what assessments were tagged
	 * with (the root cause of "report card is blank even though it says Published").
	 */
	@Transactional(readOnly = true)
	public List<TermSummaryResponse> listTerms(UUID sectionId) {
		classSectionService.getScopedClassSection(sectionId);
		UUID schoolId = schoolContext.getSchoolId();
		return assessmentRepository.findDistinctTermsBySchoolIdAndSectionId(schoolId, sectionId).stream()
				.map(term -> new TermSummaryResponse(term, reportCardPublicationRepository.existsByClassSection_IdAndTerm(sectionId, term)))
				.toList();
	}

	/**
	 * Repairs already-broken data: an assessment created without a term (the field is optional)
	 * can never match any published term string, so its marks silently never appear on any report
	 * card. Lets an admin/class-teacher retroactively tag every untermed assessment in a section at
	 * once, rather than having to re-create them. Same authority as publishing a report card.
	 */
	@Transactional
	public BackfillTermResponse backfillTerm(UUID sectionId, String term) {
		ClassSection section = classSectionService.getScopedClassSection(sectionId);
		requireCanManageTerms(section);
		String normalized = normalizeTerm(term);
		UUID schoolId = schoolContext.getSchoolId();
		List<Assessment> untermed = assessmentRepository.findAllBySchoolIdAndSectionIdAndTermIsNull(schoolId, sectionId);
		untermed.forEach(a -> a.setTerm(normalized));
		assessmentRepository.saveAll(untermed);
		return new BackfillTermResponse(untermed.size());
	}

	private void requireCanManageTerms(ClassSection section) {
		AuthPrincipal principal = AuthContext.current();
		boolean isClassTeacher = principal.getRole() == Role.TEACHER
				&& section.getClassTeacher() != null
				&& section.getClassTeacher().getId().equals(principal.getOwnerId());
		if (principal.getRole() != Role.ADMIN && !isClassTeacher) {
			throw new AccessDeniedException("Only an admin or this section's class teacher can backfill assessment terms");
		}
	}

	private String normalizeTerm(String term) {
		if (term == null) {
			return null;
		}
		String trimmed = term.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private void applyRequest(Assessment assessment, AssessmentRequest request) {
		assessment.setTitle(request.getTitle());
		assessment.setType(request.getType());
		assessment.setAssessmentDate(request.getAssessmentDate());
		assessment.setMaxMarks(request.getMaxMarks());
		assessment.setDescription(request.getDescription());
		assessment.setTerm(normalizeTerm(request.getTerm()));

		if (request.getSubjectId() != null) {
			Subject subject = subjectRepository.findByIdAndSchoolId(request.getSubjectId(), schoolContext.getSchoolId())
					.orElseThrow(() -> new EntityNotFoundException("Subject not found"));
			assessment.setSubject(subject);
		} else {
			assessment.setSubject(null);
		}

		if (request.getTeacherId() != null) {
			Employee teacher = employeeService.getScopedEntity(request.getTeacherId());
			assessment.setCreatedByTeacher(teacher);
		} else {
			assessment.setCreatedByTeacher(null);
		}
	}

	private Assessment findScoped(UUID id) {
		return assessmentRepository.findByIdAndSchoolId(id, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Assessment not found"));
	}

}
