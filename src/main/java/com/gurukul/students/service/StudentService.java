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
	private final com.gurukul.documents.DocumentNumberGenerator documentNumberGenerator;

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
		ClassSection classSection = classSectionService.getScopedClassSection(request.getClassSectionId());

		Student student = new Student();
		student.setSchoolId(schoolId);
		applyRequest(student, request, classSection);
		student.setStatus(StudentStatus.ACTIVE);
		// Placeholder only - recomputeActiveRollNumbers overwrites it below with the real alphabetical rank.
		student.setRollNumber("PENDING-" + UUID.randomUUID());

		String admissionYear = String.valueOf(student.getAdmissionDate().getYear());
		student.setRegistrationNumber(documentNumberGenerator.nextRegistrationNumber(schoolId, admissionYear));

		Student saved = studentRepository.save(student);
		feeStructureService.createAssessmentForStudentIfStructureExists(saved);
		recomputeActiveRollNumbers(classSection.getId());

		return saved;
	}

	@Transactional
	public StudentResponse updateClassSection(UUID id, StudentClassSectionUpdateRequest request) {
		Student student = findScoped(id);
		UUID oldClassSectionId = student.getClassSection().getId();
		ClassSection classSection = classSectionService.getScopedClassSection(request.getClassSectionId());
		student.setClassSection(classSection);
		// Carrying the old section's roll number into the new section's namespace can collide with
		// an existing student there the moment anything triggers a flush - neutralize it first.
		student.setRollNumber("TMP-" + student.getId());
		Student saved = studentRepository.save(student);

		recomputeActiveRollNumbers(oldClassSectionId);
		if (!oldClassSectionId.equals(classSection.getId())) {
			recomputeActiveRollNumbers(classSection.getId());
		}
		return StudentResponse.from(saved);
	}

	@Transactional
	public StudentResponse update(UUID id, StudentRequest request) {
		Student student = findScoped(id);
		UUID oldClassSectionId = student.getClassSection().getId();

		ClassSection classSection = classSectionService.getScopedClassSection(request.getClassSectionId());
		applyRequest(student, request, classSection);
		if (request.getStatus() != null) {
			student.setStatus(request.getStatus());
		}
		// Neutralize the roll number before the first save, regardless of what changed - carrying
		// the old value forward (into a new section's namespace, or while now inactive) can collide
		// with an existing row the moment anything triggers a flush. recompute() below assigns the
		// real value for anyone still ACTIVE; INACTIVE-* is the final value for anyone who isn't.
		student.setRollNumber(student.getStatus() == StudentStatus.ACTIVE
				? "TMP-" + student.getId()
				: "INACTIVE-" + student.getId());

		Student saved = studentRepository.save(student);

		recomputeActiveRollNumbers(oldClassSectionId);
		if (!oldClassSectionId.equals(classSection.getId())) {
			recomputeActiveRollNumbers(classSection.getId());
		}
		return StudentResponse.from(saved);
	}

	@Transactional
	public void delete(UUID id) {
		Student student = findScoped(id);
		UUID classSectionId = student.getClassSection().getId();
		studentRepository.delete(student);
		recomputeActiveRollNumbers(classSectionId);
	}

	private Student findScoped(UUID id) {
		return studentRepository.findByIdAndSchoolId(id, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Student not found"));
	}

	private void applyRequest(Student student, StudentRequest request, ClassSection classSection) {
		student.setName(request.getName());
		student.setDob(request.getDob());
		student.setGender(request.getGender());
		student.setAddress(request.getAddress());
		student.setParentName(request.getParentName());
		student.setParentContact(request.getParentContact());
		student.setClassSection(classSection);
		student.setAdmissionDate(request.getAdmissionDate());
	}

	/**
	 * Roll number is server-computed: 1-indexed alphabetical rank of ACTIVE students within a
	 * class-section (ties broken by admission date, then creation time). Reassigns every active
	 * student's roll number in the section whenever the roster changes - a name edit can reorder the
	 * whole section, not just shift a tail. Runs in two passes (temp values, then final values) so
	 * the in-progress reshuffle never trips the (class_section_id, roll_number) unique constraint.
	 */
	private void recomputeActiveRollNumbers(UUID classSectionId) {
		List<Student> ordered = studentRepository
				.findAllByClassSectionIdAndStatus(classSectionId, StudentStatus.ACTIVE).stream()
				.sorted(Comparator.comparing(Student::getName, String.CASE_INSENSITIVE_ORDER)
						.thenComparing(Student::getAdmissionDate)
						.thenComparing(Student::getCreatedAt))
				.toList();

		for (Student s : ordered) {
			s.setRollNumber("TMP-" + s.getId());
		}
		studentRepository.saveAll(ordered);
		studentRepository.flush();

		for (int i = 0; i < ordered.size(); i++) {
			ordered.get(i).setRollNumber(String.valueOf(i + 1));
		}
		studentRepository.saveAll(ordered);
	}

}
