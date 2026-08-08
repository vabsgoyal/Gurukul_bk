package com.gurukul.exams.service;

import com.gurukul.academics.entity.Subject;
import com.gurukul.academics.repository.SubjectRepository;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.service.EmployeeService;
import com.gurukul.exams.dto.AssessmentRequest;
import com.gurukul.exams.dto.AssessmentResponse;
import com.gurukul.exams.entity.Assessment;
import com.gurukul.exams.entity.AssessmentType;
import com.gurukul.exams.repository.AssessmentRepository;
import com.gurukul.students.entity.ClassSection;
import com.gurukul.students.service.ClassSectionService;
import lombok.RequiredArgsConstructor;
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

	public Assessment getScopedEntity(UUID id) {
		return findScoped(id);
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

	private void applyRequest(Assessment assessment, AssessmentRequest request) {
		assessment.setTitle(request.getTitle());
		assessment.setType(request.getType());
		assessment.setAssessmentDate(request.getAssessmentDate());
		assessment.setMaxMarks(request.getMaxMarks());
		assessment.setDescription(request.getDescription());

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
