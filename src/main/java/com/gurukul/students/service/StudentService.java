package com.gurukul.students.service;

import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.FuzzyMatcher;
import com.gurukul.common.SchoolContext;
import com.gurukul.students.dto.StudentClassSectionUpdateRequest;
import com.gurukul.students.dto.StudentRequest;
import com.gurukul.students.dto.StudentResponse;
import com.gurukul.students.entity.ClassSection;
import com.gurukul.students.entity.Student;
import com.gurukul.students.entity.StudentStatus;
import com.gurukul.students.repository.StudentRepository;
import com.gurukul.fees.service.FeeStructureService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentService {

	private static final int SEARCH_RESULT_LIMIT = 50;

	private final StudentRepository studentRepository;
	private final SchoolContext schoolContext;
	private final ClassSectionService classSectionService;
	private final FeeStructureService feeStructureService;

	@Transactional(readOnly = true)
	public List<StudentResponse> list() {
		return studentRepository.findAllBySchoolId(schoolContext.getSchoolId()).stream()
				.map(StudentResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<StudentResponse> search(String query) {
		requireQuery(query);
		return studentRepository.findAllBySchoolId(schoolContext.getSchoolId()).stream()
				.filter(s -> FuzzyMatcher.anyFieldMatches(query, s.getName(), s.getRollNumber()))
				.sorted(Comparator.comparingDouble(
						(Student s) -> FuzzyMatcher.bestScore(query, s.getName(), s.getRollNumber())).reversed())
				.limit(SEARCH_RESULT_LIMIT)
				.map(StudentResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<StudentResponse> searchByParent(String query) {
		requireQuery(query);
		return studentRepository.findAllBySchoolId(schoolContext.getSchoolId()).stream()
				.filter(s -> FuzzyMatcher.anyFieldMatches(query, s.getParentName(), s.getParentContact(), s.getName()))
				.sorted(Comparator.comparingDouble((Student s) -> FuzzyMatcher.bestScore(
						query, s.getParentName(), s.getParentContact(), s.getName())).reversed())
				.limit(SEARCH_RESULT_LIMIT)
				.map(StudentResponse::from)
				.toList();
	}

	private void requireQuery(String query) {
		if (query == null || query.isBlank()) {
			throw new IllegalArgumentException("Search query must not be blank");
		}
	}

	@Transactional(readOnly = true)
	public List<StudentResponse> listByClassSection(String className, String section, String academicYear) {
		ClassSection classSection = classSectionService.getScopedClassSection(className, section, academicYear);
		return listByClassSectionId(classSection.getId());
	}

	@Transactional(readOnly = true)
	public List<StudentResponse> listByClassSectionId(UUID classSectionId) {
		classSectionService.getScopedClassSection(classSectionId);
		return studentRepository.findAllBySchoolIdAndClassSectionId(schoolContext.getSchoolId(), classSectionId)
				.stream()
				.map(StudentResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public StudentResponse getById(UUID id) {
		return StudentResponse.from(findScoped(id));
	}

	public Student getScopedEntity(UUID id) {
		return findScoped(id);
	}

	@Transactional
	public StudentResponse create(StudentRequest request) {
		return StudentResponse.from(createEntity(request));
	}

	/** Returns the entity (not just its response DTO) - used by RegistrationService, which needs the id for a Credential. */
	@Transactional
	public Student createEntity(StudentRequest request) {
		UUID schoolId = schoolContext.getSchoolId();
		if (studentRepository.existsBySchoolIdAndRollNumber(schoolId, request.getRollNumber())) {
			throw new IllegalArgumentException("Roll number already exists for this school");
		}

		ClassSection classSection = classSectionService.getScopedClassSection(request.getClassSectionId());

		Student student = new Student();
		student.setSchoolId(schoolId);
		applyRequest(student, request, classSection);
		student.setStatus(StudentStatus.ACTIVE);

		Student saved = studentRepository.save(student);
		feeStructureService.createAssessmentForStudentIfStructureExists(saved);

		return saved;
	}

	@Transactional
	public StudentResponse updateClassSection(UUID id, StudentClassSectionUpdateRequest request) {
		Student student = findScoped(id);
		ClassSection classSection = classSectionService.getScopedClassSection(request.getClassSectionId());
		student.setClassSection(classSection);
		return StudentResponse.from(studentRepository.save(student));
	}

	@Transactional
	public StudentResponse update(UUID id, StudentRequest request) {
		Student student = findScoped(id);
		UUID schoolId = schoolContext.getSchoolId();

		if (studentRepository.existsBySchoolIdAndRollNumberAndIdNot(
				schoolId, request.getRollNumber(), id)) {
			throw new IllegalArgumentException("Roll number already exists for this school");
		}

		ClassSection classSection = classSectionService.getScopedClassSection(request.getClassSectionId());
		applyRequest(student, request, classSection);
		if (request.getStatus() != null) {
			student.setStatus(request.getStatus());
		}

		return StudentResponse.from(studentRepository.save(student));
	}

	@Transactional
	public void delete(UUID id) {
		Student student = findScoped(id);
		studentRepository.delete(student);
	}

	private Student findScoped(UUID id) {
		return studentRepository.findByIdAndSchoolId(id, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Student not found"));
	}

	private void applyRequest(Student student, StudentRequest request, ClassSection classSection) {
		student.setRollNumber(request.getRollNumber());
		student.setName(request.getName());
		student.setDob(request.getDob());
		student.setGender(request.getGender());
		student.setAddress(request.getAddress());
		student.setParentName(request.getParentName());
		student.setParentContact(request.getParentContact());
		student.setClassSection(classSection);
		student.setAdmissionDate(request.getAdmissionDate());
	}

}
