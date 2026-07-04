package com.gurukul.students.service;

import com.gurukul.common.SchoolContext;
import com.gurukul.students.dto.ClassSectionRequest;
import com.gurukul.students.dto.ClassSectionResponse;
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
	private final SchoolContext schoolContext;

	public List<ClassSectionResponse> list() {
		return classSectionRepository.findAllBySchoolIdOrderByClassNameAscSectionAsc(schoolContext.getSchoolId())
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
