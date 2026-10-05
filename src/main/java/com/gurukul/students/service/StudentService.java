package com.gurukul.students.service;

import com.gurukul.common.PartialUpdate;
import com.gurukul.auth.entity.OwnerType;
import org.springframework.security.access.AccessDeniedException;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthContext;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.crypto.TokenCipher;
import com.gurukul.common.FuzzyMatcher;
import com.gurukul.common.PageResponse;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Map;
import java.util.LinkedHashMap;
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
	private final TokenCipher tokenCipher;

	@Transactional(readOnly = true)
	public PageResponse<StudentResponse> list(int page, int size) {
		boolean includeRegistrationNumber = isAdmin();
		UUID schoolId = schoolContext.getSchoolId();
		Slice<Student> result = studentRepository.findAllBySchoolIdOrderByNameAsc(schoolId, PageRequest.of(page, size));
		Long totalElements = page == 0 ? studentRepository.countBySchoolId(schoolId) : null;
		return new PageResponse<>(
				result.getContent().stream().map(s -> StudentResponse.from(s, includeRegistrationNumber, tokenCipher)).toList(),
				result.hasNext(),
				totalElements);
	}

	@Transactional(readOnly = true)
	public List<StudentResponse> search(String query) {
		requireQuery(query);
		boolean includeRegistrationNumber = isAdmin();
		return studentRepository.findAllBySchoolId(schoolContext.getSchoolId()).stream()
				.filter(s -> FuzzyMatcher.anyFieldMatches(query, s.getName(), s.getRollNumber()))
				.sorted(Comparator.comparingDouble(
						(Student s) -> FuzzyMatcher.bestScore(query, s.getName(), s.getRollNumber())).reversed())
				.limit(SEARCH_RESULT_LIMIT)
				.map(s -> StudentResponse.from(s, includeRegistrationNumber, tokenCipher))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<StudentResponse> searchByParent(String query) {
		requireQuery(query);
		boolean includeRegistrationNumber = isAdmin();
		return studentRepository.findAllBySchoolId(schoolContext.getSchoolId()).stream()
				.filter(s -> FuzzyMatcher.anyFieldMatches(query, s.getParentName(), s.getParentContact(), s.getName()))
				.sorted(Comparator.comparingDouble((Student s) -> FuzzyMatcher.bestScore(
						query, s.getParentName(), s.getParentContact(), s.getName())).reversed())
				.limit(SEARCH_RESULT_LIMIT)
				.map(s -> StudentResponse.from(s, includeRegistrationNumber, tokenCipher))
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
		boolean includeRegistrationNumber = isAdmin();
		return studentRepository.findAllBySchoolIdAndClassSectionId(schoolContext.getSchoolId(), classSectionId)
				.stream()
				.sorted(Comparator.comparingInt(StudentService::rollNumberSortKey)
						.thenComparing(Student::getName, String.CASE_INSENSITIVE_ORDER))
				.map(s -> StudentResponse.from(s, includeRegistrationNumber, tokenCipher))
				.toList();
	}

	@Transactional(readOnly = true)
	public StudentResponse getById(UUID id) {
		return StudentResponse.from(findScoped(id), isAdmin(), tokenCipher);
	}

	public Student getScopedEntity(UUID id) {
		return findScoped(id);
	}

	@Transactional
	public StudentResponse create(StudentRequest request) {
		// Admins and teachers add students (teachers inline from the app); checked here too so the rule
		// holds whatever the route config says. The response uses the caller's real role, like every
		// other read, so the registrationNumber and decrypted Aadhaar/bank fields go to admins only.
		// createEntity itself stays unguarded: bulk import and admissions call it after their own checks.
		if (!isAdmin() && !hasRole(Role.TEACHER)) {
			throw new AccessDeniedException("Only an admin or teacher can add a student");
		}
		return StudentResponse.from(createEntity(request), isAdmin(), tokenCipher);
	}

	private boolean isAdmin() {
		return hasRole(Role.ADMIN);
	}

	private static boolean hasRole(Role role) {
		AuthPrincipal principal = AuthContext.currentOrNull();
		return principal != null && principal.getRole() == role;
	}

	private static boolean isSelf(UUID studentId) {
		AuthPrincipal principal = AuthContext.currentOrNull();
		return principal != null && principal.getOwnerType() == OwnerType.STUDENT
				&& principal.getOwnerId().equals(studentId);
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
		return StudentResponse.from(saved, isAdmin(), tokenCipher);
	}

	@Transactional
	public StudentResponse update(UUID id, StudentRequest request) {
		// parentContact is the student's OTP login, so changing another student's would take it over.
		if (!isAdmin() && !isSelf(id)) {
			throw new AccessDeniedException("You can only edit your own student record");
		}
		Student student = findScoped(id);
		UUID oldClassSectionId = student.getClassSection().getId();
		String oldName = student.getName();
		LocalDate oldAdmissionDate = student.getAdmissionDate();
		StudentStatus oldStatus = student.getStatus();

		ClassSection classSection = classSectionService.getScopedClassSection(request.getClassSectionId());
		applyRequest(student, request, classSection);
		if (request.getStatus() != null) {
			student.setStatus(request.getStatus());
		}

		// Roll numbers are a rank by name, then admission date, within the active roster of a
		// section. An edit that touches none of those (address, phone, parent details...) can't
		// move anyone, so it saves just this one row.
		boolean rosterOrderChanged = !Objects.equals(oldName, student.getName())
				|| !Objects.equals(oldAdmissionDate, student.getAdmissionDate())
				|| oldStatus != student.getStatus()
				|| !oldClassSectionId.equals(classSection.getId());
		if (!rosterOrderChanged) {
			return StudentResponse.from(studentRepository.save(student), isAdmin(), tokenCipher);
		}

		// Neutralize the roll number before the first save - carrying the old value forward (into a
		// new section's namespace, or while now inactive) can collide with an existing row the moment
		// anything triggers a flush. recompute() below assigns the real value for anyone still
		// ACTIVE; INACTIVE-* is the final value for anyone who isn't.
		student.setRollNumber(student.getStatus() == StudentStatus.ACTIVE
				? "TMP-" + student.getId()
				: "INACTIVE-" + student.getId());

		Student saved = studentRepository.save(student);

		recomputeActiveRollNumbers(oldClassSectionId);
		if (!oldClassSectionId.equals(classSection.getId())) {
			recomputeActiveRollNumbers(classSection.getId());
		}
		return StudentResponse.from(saved, isAdmin(), tokenCipher);
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

	/**
	 * rollNumber is a String (see Student.rollNumber), so a naive sort puts "10" before "2" - parse it
	 * for the real numeric order. Non-ACTIVE students carry a permanent non-numeric placeholder
	 * ("INACTIVE-<uuid>", see update()) instead of ever getting a real number back, so those sort
	 * after every numbered student rather than erroring.
	 */
	private static int rollNumberSortKey(Student student) {
		try {
			return Integer.parseInt(student.getRollNumber());
		} catch (NumberFormatException e) {
			return Integer.MAX_VALUE;
		}
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

		// RTE fields: the app's edit form doesn't send them, so a field left out keeps its current
		// value and "" clears it (see PartialUpdate) - otherwise every app edit wiped them.
		student.setSssmId(PartialUpdate.text(student.getSssmId(), request.getSssmId()));
		student.setAadhaarNumberEncrypted(encryptedUpdate(student.getAadhaarNumberEncrypted(), request.getAadhaarNumber()));
		student.setCaste(PartialUpdate.text(student.getCaste(), request.getCaste()));
		student.setCategory(PartialUpdate.text(student.getCategory(), request.getCategory()));
		student.setAnnualIncome(PartialUpdate.value(student.getAnnualIncome(), request.getAnnualIncome()));
		student.setPreviousSchoolName(PartialUpdate.text(student.getPreviousSchoolName(), request.getPreviousSchoolName()));
		student.setBankAccountNumberEncrypted(
				encryptedUpdate(student.getBankAccountNumberEncrypted(), request.getBankAccountNumber()));
		student.setBankIfsc(PartialUpdate.text(student.getBankIfsc(), request.getBankIfsc()));
	}

	private String encryptedUpdate(String currentCiphertext, String sentPlaintext) {
		String value = PartialUpdate.text(null, sentPlaintext);
		if (sentPlaintext == null) {
			return currentCiphertext;
		}
		return value == null ? null : tokenCipher.encrypt(value);
	}

	/**
	 * Roll number is server-computed: 1-indexed alphabetical rank of ACTIVE students within a
	 * class-section (ties broken by admission date, then creation time), recomputed whenever the
	 * roster changes - a name edit can reorder the whole section, not just shift a tail.
	 *
	 * <p>Only students whose rank actually moves are written. They go through two passes (temp
	 * values, then final values) so the reshuffle never trips the (class_section_id, roll_number)
	 * unique constraint; everyone else already holds their final number, and final numbers are
	 * unique, so they can't collide. Each pass is one JDBC batch (hibernate.jdbc.batch_size).
	 */
	private void recomputeActiveRollNumbers(UUID classSectionId) {
		List<Student> ordered = studentRepository
				.findAllByClassSectionIdAndStatus(classSectionId, StudentStatus.ACTIVE).stream()
				.sorted(Comparator.comparing(Student::getName, String.CASE_INSENSITIVE_ORDER)
						.thenComparing(Student::getAdmissionDate)
						.thenComparing(Student::getCreatedAt))
				.toList();

		Map<Student, String> moving = new LinkedHashMap<>();
		for (int i = 0; i < ordered.size(); i++) {
			String rank = String.valueOf(i + 1);
			if (!rank.equals(ordered.get(i).getRollNumber())) {
				moving.put(ordered.get(i), rank);
			}
		}
		if (moving.isEmpty()) {
			return;
		}

		moving.keySet().forEach(s -> s.setRollNumber("TMP-" + s.getId()));
		studentRepository.flush();
		moving.forEach(Student::setRollNumber);
		studentRepository.flush();
	}

}
