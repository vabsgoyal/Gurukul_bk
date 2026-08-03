package com.gurukul.students.service;

import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.service.EmployeeService;
import com.gurukul.students.dto.ClassSectionRequest;
import com.gurukul.students.dto.ClassSectionResponse;
import com.gurukul.students.dto.ClassTeacherAssignmentRequest;
import com.gurukul.students.entity.ClassSection;
import com.gurukul.students.repository.ClassSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClassSectionService {

	private final ClassSectionRepository classSectionRepository;
	private final EmployeeService employeeService;
	private final SchoolContext schoolContext;

	@Transactional(readOnly = true)
	public List<ClassSectionResponse> list() {
		return classSectionRepository.findAllBySchoolIdOrderByClassNameAscSectionAsc(schoolContext.getSchoolId())
				.stream()
				.map(ClassSectionResponse::from)
				.toList();
	}

	public List<String> listClassNames() {
		return classSectionRepository.findDistinctClassNamesBySchoolId(schoolContext.getSchoolId());
	}

	@Transactional(readOnly = true)
	public List<ClassSectionResponse> listByClassName(String className) {
		return classSectionRepository.findAllBySchoolIdAndClassNameOrderBySectionAsc(schoolContext.getSchoolId(), className)
				.stream()
				.map(ClassSectionResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public ClassSectionResponse getById(UUID id) {
		return ClassSectionResponse.from(classSectionRepository.findByIdAndSchoolId(id, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Class-section not found")));
	}

	@Transactional
	public ClassSectionResponse assignClassTeacher(UUID sectionId, ClassTeacherAssignmentRequest request) {
		ClassSection section = classSectionRepository.findByIdAndSchoolId(sectionId, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Class-section not found"));
		Employee teacher = employeeService.getScopedEntity(request.getTeacherId());

		if (classSectionRepository.existsBySchoolIdAndClassTeacherIdAndAcademicYearAndIdNot(
				schoolContext.getSchoolId(), teacher.getId(), section.getAcademicYear(), sectionId)) {
			throw new IllegalArgumentException(
					"This teacher is already the class teacher of another section in this academic year");
		}

		section.setClassTeacher(teacher);
		return ClassSectionResponse.from(classSectionRepository.save(section));
	}

	@Transactional(readOnly = true)
	public List<ClassSectionResponse> listByClassTeacherId(UUID employeeId) {
		employeeService.getScopedEntity(employeeId);
		return classSectionRepository
				.findAllBySchoolIdAndClassTeacherIdOrderByAcademicYearDesc(schoolContext.getSchoolId(), employeeId)
				.stream()
				.map(ClassSectionResponse::from)
				.toList();
	}

	@Transactional
	public ClassSectionResponse create(ClassSectionRequest request) {
		UUID schoolId = schoolContext.getSchoolId();
		if (classSectionRepository.existsBySchoolIdAndClassNameAndSectionAndAcademicYear(
				schoolId, request.getClassName(), request.getSection(), request.getAcademicYear())) {
			throw new IllegalArgumentException("Class-section already exists for this school");
		}

		ClassSection classSection = new ClassSection();
		classSection.setSchoolId(schoolId);
		classSection.setClassName(request.getClassName());
		classSection.setSection(request.getSection());
		classSection.setAcademicYear(request.getAcademicYear());

		return ClassSectionResponse.from(classSectionRepository.save(classSection));
	}

	public ClassSection getScopedClassSection(UUID classSectionId) {
		return classSectionRepository.findByIdAndSchoolId(classSectionId, schoolContext.getSchoolId())
				.orElseThrow(() -> new IllegalArgumentException("Class-section not found for this school"));
	}

	public ClassSection getScopedClassSection(String className, String section, String academicYear) {
		return classSectionRepository.findBySchoolIdAndClassNameAndSectionAndAcademicYear(
						schoolContext.getSchoolId(), className, section, academicYear)
				.orElseThrow(() -> new IllegalArgumentException("Class-section not found for this school"));
	}

}
