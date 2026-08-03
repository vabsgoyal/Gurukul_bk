package com.gurukul.academics.service;

import com.gurukul.academics.dto.AcademicsDtos.SectionSubjectRequest;
import com.gurukul.academics.dto.AcademicsDtos.SubjectAssignmentResponse;
import com.gurukul.academics.dto.AcademicsDtos.SubjectRequest;
import com.gurukul.academics.dto.AcademicsDtos.SubjectResponse;
import com.gurukul.academics.dto.AcademicsDtos.TeacherAssignmentResponse;
import com.gurukul.academics.entity.SectionSubjectTeacher;
import com.gurukul.academics.entity.Subject;
import com.gurukul.academics.repository.SectionSubjectTeacherRepository;
import com.gurukul.academics.repository.SubjectRepository;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.service.EmployeeService;
import com.gurukul.students.entity.ClassSection;
import com.gurukul.students.service.ClassSectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AcademicsService {

	private final SubjectRepository subjectRepository;
	private final SectionSubjectTeacherRepository sectionSubjectTeacherRepository;
	private final ClassSectionService classSectionService;
	private final EmployeeService employeeService;
	private final SchoolContext schoolContext;

	public List<SubjectResponse> listSubjects() {
		return subjectRepository.findAllBySchoolIdOrderByCodeAsc(schoolContext.getSchoolId()).stream()
				.map(AcademicsService::toSubjectResponse)
				.toList();
	}

	public SubjectResponse getSubject(UUID id) {
		return toSubjectResponse(findSubject(id));
	}

	@Transactional
	public SubjectResponse createSubject(SubjectRequest request) {
		UUID schoolId = schoolContext.getSchoolId();
		if (subjectRepository.findBySchoolIdAndCode(schoolId, request.getCode()).isPresent()) {
			throw new IllegalArgumentException("Subject code already exists for this school");
		}
		Subject subject = new Subject();
		subject.setSchoolId(schoolId);
		subject.setCode(request.getCode());
		subject.setName(request.getName());
		subject.setDescription(request.getDescription());
		return toSubjectResponse(subjectRepository.save(subject));
	}

	@Transactional(readOnly = true)
	public List<SubjectAssignmentResponse> listSectionSubjects(UUID sectionId) {
		classSectionService.getScopedClassSection(sectionId);
		return sectionSubjectTeacherRepository.findAllBySectionId(sectionId).stream()
				.map(AcademicsService::toSubjectAssignmentResponse)
				.toList();
	}

	@Transactional
	public SubjectAssignmentResponse assignSubjectToSection(UUID sectionId, SectionSubjectRequest request) {
		ClassSection section = classSectionService.getScopedClassSection(sectionId);
		Subject subject = findSubject(request.getSubjectId());
		Employee teacher = employeeService.getScopedEntity(request.getTeacherId());

		if (sectionSubjectTeacherRepository
				.findBySectionIdAndSubjectIdAndTeacherId(sectionId, subject.getId(), teacher.getId())
				.isPresent()) {
			throw new IllegalArgumentException("Subject is already assigned to this teacher for this section");
		}

		SectionSubjectTeacher assignment = new SectionSubjectTeacher();
		assignment.setSchoolId(schoolContext.getSchoolId());
		assignment.setSection(section);
		assignment.setSubject(subject);
		assignment.setTeacher(teacher);
		return toSubjectAssignmentResponse(sectionSubjectTeacherRepository.save(assignment));
	}

	@Transactional(readOnly = true)
	public List<TeacherAssignmentResponse> listAssignmentsForTeacher(UUID teacherId) {
		employeeService.getScopedEntity(teacherId);
		return sectionSubjectTeacherRepository.findAllByTeacherId(teacherId).stream()
				.map(AcademicsService::toTeacherAssignmentResponse)
				.toList();
	}

	private Subject findSubject(UUID id) {
		return subjectRepository.findByIdAndSchoolId(id, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Subject not found"));
	}

	private static SubjectResponse toSubjectResponse(Subject subject) {
		return new SubjectResponse(subject.getId(), subject.getCode(), subject.getName(), subject.getDescription());
	}

	private static SubjectAssignmentResponse toSubjectAssignmentResponse(SectionSubjectTeacher assignment) {
		return new SubjectAssignmentResponse(
				assignment.getSubject().getId(),
				assignment.getSubject().getName(),
				assignment.getSubject().getCode(),
				assignment.getTeacher().getId(),
				assignment.getTeacher().getName()
		);
	}

	private static TeacherAssignmentResponse toTeacherAssignmentResponse(SectionSubjectTeacher assignment) {
		return new TeacherAssignmentResponse(
				assignment.getSection().getId(),
				assignment.getSection().getClassName(),
				assignment.getSection().getSection(),
				assignment.getSection().getAcademicYear(),
				assignment.getSubject().getId(),
				assignment.getSubject().getName(),
				assignment.getSubject().getCode()
		);
	}

}
