package com.gurukul.schools.service;

import com.gurukul.common.EntityNotFoundException;
import com.gurukul.schools.dto.SchoolRegistrationRequest;
import com.gurukul.schools.dto.SchoolResponse;
import com.gurukul.schools.entity.School;
import com.gurukul.schools.repository.SchoolRepository;
import com.gurukul.students.repository.ClassSectionRepository;
import com.gurukul.students.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SchoolService {

	private final SchoolRepository schoolRepository;
	private final StudentRepository studentRepository;
	private final ClassSectionRepository classSectionRepository;

	@Transactional
	public SchoolResponse register(SchoolRegistrationRequest request) {
		School school = new School();
		school.setName(request.getName());
		school.setAddress(request.getAddress());
		school.setCity(request.getCity());
		school.setState(request.getState());
		school.setPincode(request.getPincode());
		school.setContactEmail(request.getContactEmail());
		school.setContactPhone(request.getContactPhone());
		school.setPrincipalName(request.getPrincipalName());
		school.setDirectorName(request.getDirectorName());
		School saved = schoolRepository.save(school);
		return toResponse(saved);
	}

	public SchoolResponse getById(UUID id) {
		return toResponse(findSchool(id));
	}

	public void requireExists(UUID id) {
		findSchool(id);
	}

	private SchoolResponse toResponse(School school) {
		UUID schoolId = school.getId();
		long studentCount = studentRepository.countBySchoolId(schoolId);
		long classSectionCount = classSectionRepository.countBySchoolId(schoolId);
		long teacherCount = 0L;
		return SchoolResponse.from(school, studentCount, classSectionCount, teacherCount);
	}

	private School findSchool(UUID id) {
		return schoolRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("School not found"));
	}

}
