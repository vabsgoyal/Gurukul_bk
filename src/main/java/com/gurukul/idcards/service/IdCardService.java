package com.gurukul.idcards.service;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthContext;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.entity.EmployeeStatus;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.idcards.dto.IdCardDtos.IdCardResponse;
import com.gurukul.idcards.dto.IdCardDtos.IdCardVerifyResponse;
import com.gurukul.idcards.dto.IdCardDtos.PresignPhotoRequest;
import com.gurukul.idcards.dto.IdCardDtos.PresignPhotoResponse;
import com.gurukul.idcards.dto.IdCardDtos.UpdateIdCardProfileRequest;
import com.gurukul.idcards.entity.IdCardOwnerType;
import com.gurukul.idcards.entity.IdCardProfile;
import com.gurukul.idcards.repository.IdCardProfileRepository;
import com.gurukul.parents.repository.ParentStudentLinkRepository;
import com.gurukul.parents.service.ParentService;
import com.gurukul.schools.entity.School;
import com.gurukul.schools.repository.SchoolRepository;
import com.gurukul.students.entity.ClassSection;
import com.gurukul.students.entity.Student;
import com.gurukul.students.entity.StudentStatus;
import com.gurukul.students.repository.ClassSectionRepository;
import com.gurukul.students.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ID-card data and the rules for who may see or change it. Every other ID-card entry point (the
 * PDFs, the photo upload) goes through {@link #requireStudent}/{@link #requireEmployee} here.
 *
 * <ul>
 *   <li><b>Edit</b> a student's details: that student, or a parent linked to them. Nobody else - the
 *   details are profile-driven, so not even an admin.</li>
 *   <li><b>Edit</b> an employee's details: that employee only.</li>
 *   <li><b>View</b> a card: the person themselves, a linked parent (students), or an admin of the school.</li>
 *   <li>Every lookup is scoped to the caller's own school, so nothing works across schools.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class IdCardService {

	public static final Set<String> BLOOD_GROUPS = Set.of("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-");
	private static final Pattern PHONE = Pattern.compile("\\+?[0-9]{7,15}");

	public static final String MISSING_PHOTO = "PHOTO";
	public static final String MISSING_BLOOD_GROUP = "BLOOD_GROUP";
	public static final String MISSING_EMERGENCY_CONTACT = "EMERGENCY_CONTACT";

	public enum Access { VIEW, EDIT }

	private final StudentRepository studentRepository;
	private final ClassSectionRepository classSectionRepository;
	private final EmployeeRepository employeeRepository;
	private final SchoolRepository schoolRepository;
	private final IdCardProfileRepository profileRepository;
	private final ParentService parentService;
	private final ParentStudentLinkRepository parentStudentLinkRepository;
	private final ProfilePhotoService photoService;
	private final IdCardCodec codec;
	private final IdCardQrService qrService;

	// ---------------------------------------------------------------- access

	/** The student, if the caller may {@code access} their ID card; 403/404 otherwise. */
	public Student requireStudent(UUID studentId, Access access) {
		AuthPrincipal principal = AuthContext.current();
		UUID schoolId = principal.getSchoolId();
		switch (principal.getRole()) {
			case STUDENT -> {
				if (principal.getOwnerType() != OwnerType.STUDENT || !principal.getOwnerId().equals(studentId)) {
					throw new AccessDeniedException("Students can only see and edit their own ID card");
				}
			}
			case PARENT -> {
				// Throws 403 unless linked; returns the child from this school only.
				return parentService.requireLinkedChild(principal.getOwnerId(), studentId, schoolId);
			}
			case ADMIN -> {
				if (access == Access.EDIT) {
					throw new AccessDeniedException("Only the student or a linked parent can edit these ID-card details");
				}
			}
			default -> throw new AccessDeniedException("Only the student, a linked parent or an admin can see a student's ID card");
		}
		return studentRepository.findByIdAndSchoolId(studentId, schoolId)
				.orElseThrow(() -> new EntityNotFoundException("Student not found"));
	}

	/** The employee, if the caller may {@code access} their ID card; 403/404 otherwise. */
	public Employee requireEmployee(UUID employeeId, Access access) {
		AuthPrincipal principal = AuthContext.current();
		boolean self = principal.getOwnerType() == OwnerType.EMPLOYEE && principal.getOwnerId().equals(employeeId);
		boolean adminView = access == Access.VIEW && principal.getRole() == Role.ADMIN;
		if (!self && !adminView) {
			throw new AccessDeniedException(access == Access.EDIT
					? "Staff can only edit their own ID-card details"
					: "Staff can only see their own ID card");
		}
		return employeeRepository.findByIdAndSchoolId(employeeId, principal.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Employee not found"));
	}

	public static void requireAdmin() {
		if (AuthContext.current().getRole() != Role.ADMIN) {
			throw new AccessDeniedException("Only an admin can print ID-card sheets");
		}
	}

	// ---------------------------------------------------------------- reads

	@Transactional(readOnly = true)
	public List<IdCardResponse> myCards() {
		AuthPrincipal principal = AuthContext.current();
		return switch (principal.getOwnerType()) {
			case STUDENT -> List.of(getStudentCard(principal.getOwnerId()));
			case EMPLOYEE -> List.of(getEmployeeCard(principal.getOwnerId()));
			case PARENT -> parentStudentLinkRepository.findAllByParentId(principal.getOwnerId()).stream()
					.map(link -> studentRepository.findByIdAndSchoolId(link.getStudentId(), principal.getSchoolId()).orElse(null))
					.filter(Objects::nonNull)
					.map(student -> studentCard(student, school(principal.getSchoolId()), true))
					.toList();
		};
	}

	@Transactional(readOnly = true)
	public IdCardResponse getStudentCard(UUID studentId) {
		Student student = requireStudent(studentId, Access.VIEW);
		return studentCard(student, school(student.getSchoolId()), canEditStudent(studentId));
	}

	@Transactional(readOnly = true)
	public IdCardResponse getEmployeeCard(UUID employeeId) {
		Employee employee = requireEmployee(employeeId, Access.VIEW);
		return employeeCard(employee, school(employee.getSchoolId()), canEditEmployee(employeeId));
	}

	/** Identification only - who a scanned QR belongs to, for staff of the same school. */
	@Transactional(readOnly = true)
	public IdCardVerifyResponse verify(String code) {
		AuthPrincipal principal = AuthContext.current();
		if (principal.getRole() != Role.ADMIN && principal.getRole() != Role.TEACHER) {
			throw new AccessDeniedException("Only staff can verify ID cards");
		}
		UUID schoolId = principal.getSchoolId();
		IdCardCodec.Subject subject = codec.decode(code, schoolId)
				.orElseThrow(() -> new EntityNotFoundException("Not a valid ID card for this school"));
		if (subject.type() == IdCardOwnerType.STUDENT) {
			Student student = studentRepository.findByIdAndSchoolId(subject.ownerId(), schoolId)
					.orElseThrow(() -> new EntityNotFoundException("Not a valid ID card for this school"));
			return IdCardVerifyResponse.builder()
					.ownerType(IdCardOwnerType.STUDENT)
					.ownerId(student.getId())
					.name(student.getName())
					.classSectionLabel(sectionLabel(student.getClassSection()))
					.rollNumber(student.getRollNumber())
					.photoUrl(photoService.photoUrl(photoKey(profile(schoolId, IdCardOwnerType.STUDENT, student.getId()))))
					.status(student.getStatus().name())
					.active(student.getStatus() == StudentStatus.ACTIVE)
					.build();
		}
		Employee employee = employeeRepository.findByIdAndSchoolId(subject.ownerId(), schoolId)
				.orElseThrow(() -> new EntityNotFoundException("Not a valid ID card for this school"));
		return IdCardVerifyResponse.builder()
				.ownerType(IdCardOwnerType.EMPLOYEE)
				.ownerId(employee.getId())
				.name(employee.getName())
				.designation(employee.getDesignation())
				.photoUrl(photoService.photoUrl(photoKey(profile(schoolId, IdCardOwnerType.EMPLOYEE, employee.getId()))))
				.status(employee.getStatus().name())
				.active(employee.getStatus() == EmployeeStatus.ACTIVE)
				.build();
	}

	// ---------------------------------------------------------------- print data (for IdCardPdfService)

	/** One card to print, with the photo's object key so the PDF can embed the image itself. */
	public record PrintEntry(IdCardResponse card, String photoKey) {
	}

	/** Everything a PDF needs, loaded in one read-only transaction so the render can run outside it. */
	public record PrintData(School school, List<PrintEntry> entries, String filenameStem) {
	}

	@Transactional(readOnly = true)
	public PrintData studentPrintData(UUID studentId) {
		Student student = requireStudent(studentId, Access.VIEW);
		School school = school(student.getSchoolId());
		IdCardProfile profile = profile(student.getSchoolId(), IdCardOwnerType.STUDENT, studentId);
		return new PrintData(school, List.of(new PrintEntry(studentCard(student, school, profile, false, false), photoKey(profile))),
				student.getName());
	}

	@Transactional(readOnly = true)
	public PrintData employeePrintData(UUID employeeId) {
		Employee employee = requireEmployee(employeeId, Access.VIEW);
		School school = school(employee.getSchoolId());
		IdCardProfile profile = profile(employee.getSchoolId(), IdCardOwnerType.EMPLOYEE, employeeId);
		return new PrintData(school, List.of(new PrintEntry(employeeCard(employee, school, profile, false, false), photoKey(profile))),
				employee.getName());
	}

	/** Admin only: every active student of the section, in roll-number order. */
	@Transactional(readOnly = true)
	public PrintData sectionPrintData(UUID sectionId) {
		requireAdmin();
		UUID schoolId = AuthContext.current().getSchoolId();
		ClassSection section = classSectionRepository.findByIdAndSchoolId(sectionId, schoolId)
				.orElseThrow(() -> new EntityNotFoundException("Class-section not found"));
		List<Student> students = studentRepository.findAllByClassSectionIdAndStatus(section.getId(), StudentStatus.ACTIVE)
				.stream()
				.filter(student -> schoolId.equals(student.getSchoolId()))
				.sorted(Comparator.comparing(Student::getRollNumber, IdCardService::naturalCompare))
				.toList();
		if (students.isEmpty()) {
			throw new IllegalStateException("This class-section has no active students yet");
		}
		School school = school(schoolId);
		Map<UUID, IdCardProfile> profiles = profiles(schoolId, IdCardOwnerType.STUDENT, students.stream().map(Student::getId).toList());
		List<PrintEntry> entries = students.stream()
				.map(student -> {
					IdCardProfile profile = profiles.get(student.getId());
					return new PrintEntry(studentCard(student, school, profile, false, false), photoKey(profile));
				})
				.toList();
		return new PrintData(school, entries, section.getClassName() + " " + section.getSection());
	}

	/** Admin only: every active employee, by name. */
	@Transactional(readOnly = true)
	public PrintData staffPrintData() {
		requireAdmin();
		UUID schoolId = AuthContext.current().getSchoolId();
		List<Employee> employees = employeeRepository.findAllBySchoolIdOrderByNameAsc(schoolId).stream()
				.filter(employee -> employee.getStatus() == EmployeeStatus.ACTIVE)
				.toList();
		if (employees.isEmpty()) {
			throw new IllegalStateException("There are no active staff yet");
		}
		School school = school(schoolId);
		Map<UUID, IdCardProfile> profiles = profiles(schoolId, IdCardOwnerType.EMPLOYEE, employees.stream().map(Employee::getId).toList());
		List<PrintEntry> entries = employees.stream()
				.map(employee -> {
					IdCardProfile profile = profiles.get(employee.getId());
					return new PrintEntry(employeeCard(employee, school, profile, false, false), photoKey(profile));
				})
				.toList();
		return new PrintData(school, entries, "staff");
	}

	private Map<UUID, IdCardProfile> profiles(UUID schoolId, IdCardOwnerType type, List<UUID> ownerIds) {
		return profileRepository.findAllBySchoolIdAndOwnerTypeAndOwnerIdIn(schoolId, type, ownerIds).stream()
				.collect(Collectors.toMap(IdCardProfile::getOwnerId, Function.identity()));
	}

	/** "10" after "9" for roll numbers like "5", "10", "A-12"; plain text order otherwise. */
	static int naturalCompare(String a, String b) {
		String left = a == null ? "" : a;
		String right = b == null ? "" : b;
		String leftDigits = left.replaceAll("\\D", "");
		String rightDigits = right.replaceAll("\\D", "");
		if (!leftDigits.isEmpty() && !rightDigits.isEmpty() && leftDigits.length() <= 18 && rightDigits.length() <= 18) {
			int byPrefix = left.replaceAll("\\d", "").compareToIgnoreCase(right.replaceAll("\\d", ""));
			if (byPrefix != 0) {
				return byPrefix;
			}
			int byNumber = Long.compare(Long.parseLong(leftDigits), Long.parseLong(rightDigits));
			if (byNumber != 0) {
				return byNumber;
			}
		}
		return left.compareToIgnoreCase(right);
	}

	// ---------------------------------------------------------------- writes

	@Transactional
	public IdCardResponse updateStudentProfile(UUID studentId, UpdateIdCardProfileRequest request) {
		Student student = requireStudent(studentId, Access.EDIT);
		applyDetails(editableProfile(student.getSchoolId(), IdCardOwnerType.STUDENT, studentId), request);
		return studentCard(student, school(student.getSchoolId()), true);
	}

	@Transactional
	public IdCardResponse updateEmployeeProfile(UUID employeeId, UpdateIdCardProfileRequest request) {
		Employee employee = requireEmployee(employeeId, Access.EDIT);
		applyDetails(editableProfile(employee.getSchoolId(), IdCardOwnerType.EMPLOYEE, employeeId), request);
		return employeeCard(employee, school(employee.getSchoolId()), true);
	}

	public PresignPhotoResponse presignPhoto(IdCardOwnerType type, UUID ownerId, PresignPhotoRequest request) {
		UUID schoolId = requireEditable(type, ownerId);
		return photoService.presignUpload(schoolId, type, ownerId, request);
	}

	@Transactional
	public IdCardResponse setPhoto(IdCardOwnerType type, UUID ownerId, String objectKey) {
		UUID schoolId = requireEditable(type, ownerId);
		photoService.requireUploaded(schoolId, type, ownerId, objectKey);
		IdCardProfile profile = editableProfile(schoolId, type, ownerId);
		profile.setPhotoObjectKey(objectKey);
		profileRepository.save(profile);
		return type == IdCardOwnerType.STUDENT ? getStudentCard(ownerId) : getEmployeeCard(ownerId);
	}

	@Transactional
	public IdCardResponse removePhoto(IdCardOwnerType type, UUID ownerId) {
		UUID schoolId = requireEditable(type, ownerId);
		profileRepository.findBySchoolIdAndOwnerTypeAndOwnerId(schoolId, type, ownerId).ifPresent(profile -> {
			profile.setPhotoObjectKey(null);
			profile.setUpdatedBy(AuthContext.current().getUsername());
			profileRepository.save(profile);
		});
		return type == IdCardOwnerType.STUDENT ? getStudentCard(ownerId) : getEmployeeCard(ownerId);
	}

	// ---------------------------------------------------------------- building cards

	public IdCardResponse studentCard(Student student, School school, boolean canEdit) {
		return studentCard(student, school, profile(student.getSchoolId(), IdCardOwnerType.STUDENT, student.getId()), canEdit, true);
	}

	/** {@code withQrImage=false} skips the (comparatively costly) QR PNG, for callers that draw their own. */
	public IdCardResponse studentCard(Student student, School school, IdCardProfile profile, boolean canEdit, boolean withQrImage) {
		ClassSection section = student.getClassSection();
		String emergencyPhone = profile != null ? profile.getEmergencyPhone() : null;
		String qrCode = codec.encode(IdCardOwnerType.STUDENT, student.getId(), student.getSchoolId());
		List<String> missing = new ArrayList<>();
		if (photoKey(profile) == null) {
			missing.add(MISSING_PHOTO);
		}
		if (profile == null || profile.getBloodGroup() == null) {
			missing.add(MISSING_BLOOD_GROUP);
		}
		// No EMERGENCY_CONTACT for students: the parent contact on the student record is always set
		// and is what the card prints unless an emergency phone is entered.
		return IdCardResponse.builder()
				.ownerType(IdCardOwnerType.STUDENT)
				.ownerId(student.getId())
				.name(student.getName())
				.classSectionLabel(sectionLabel(section))
				.rollNumber(student.getRollNumber())
				.academicYear(section != null ? section.getAcademicYear() : null)
				.parentName(student.getParentName())
				.bloodGroup(profile != null ? profile.getBloodGroup() : null)
				.emergencyContactName(profile != null ? profile.getEmergencyContactName() : null)
				.emergencyPhone(emergencyPhone)
				.cardPhone(emergencyPhone != null ? emergencyPhone : student.getParentContact())
				.photoUrl(photoService.photoUrl(photoKey(profile)))
				.hasPhoto(photoKey(profile) != null)
				.missing(missing)
				.canEdit(canEdit)
				.status(student.getStatus().name())
				.schoolName(school.getName())
				.schoolAddress(schoolAddress(school))
				.qrCode(qrCode)
				.qrImage(withQrImage ? qrService.pngDataUri(qrCode) : null)
				.build();
	}

	public IdCardResponse employeeCard(Employee employee, School school, boolean canEdit) {
		return employeeCard(employee, school, profile(employee.getSchoolId(), IdCardOwnerType.EMPLOYEE, employee.getId()), canEdit, true);
	}

	public IdCardResponse employeeCard(Employee employee, School school, IdCardProfile profile, boolean canEdit, boolean withQrImage) {
		String emergencyPhone = profile != null ? profile.getEmergencyPhone() : null;
		String qrCode = codec.encode(IdCardOwnerType.EMPLOYEE, employee.getId(), employee.getSchoolId());
		List<String> missing = new ArrayList<>();
		if (photoKey(profile) == null) {
			missing.add(MISSING_PHOTO);
		}
		if (profile == null || profile.getBloodGroup() == null) {
			missing.add(MISSING_BLOOD_GROUP);
		}
		// Staff: their own phone isn't an emergency contact, so ask for one (the card still prints
		// the staff member's own phone as a fallback).
		if (emergencyPhone == null) {
			missing.add(MISSING_EMERGENCY_CONTACT);
		}
		return IdCardResponse.builder()
				.ownerType(IdCardOwnerType.EMPLOYEE)
				.ownerId(employee.getId())
				.name(employee.getName())
				.designation(employee.getDesignation())
				.bloodGroup(profile != null ? profile.getBloodGroup() : null)
				.emergencyContactName(profile != null ? profile.getEmergencyContactName() : null)
				.emergencyPhone(emergencyPhone)
				.cardPhone(emergencyPhone != null ? emergencyPhone : employee.getContactPhone())
				.photoUrl(photoService.photoUrl(photoKey(profile)))
				.hasPhoto(photoKey(profile) != null)
				.missing(missing)
				.canEdit(canEdit)
				.status(employee.getStatus().name())
				.schoolName(school.getName())
				.schoolAddress(schoolAddress(school))
				.qrCode(qrCode)
				.qrImage(withQrImage ? qrService.pngDataUri(qrCode) : null)
				.build();
	}

	// ---------------------------------------------------------------- validation (package-visible for tests)

	/** Normalises and validates the request onto the profile; blank clears a field. */
	void applyDetails(IdCardProfile profile, UpdateIdCardProfileRequest request) {
		profile.setBloodGroup(normaliseBloodGroup(request.getBloodGroup()));
		profile.setEmergencyContactName(blankToNull(request.getEmergencyContactName()));
		profile.setEmergencyPhone(normalisePhone(request.getEmergencyPhone()));
		profile.setUpdatedBy(AuthContext.current().getUsername());
		profileRepository.save(profile);
	}

	static String normaliseBloodGroup(String value) {
		String trimmed = blankToNull(value);
		if (trimmed == null) {
			return null;
		}
		// Accept the common spellings people type: "b +ve", "O negative", a Unicode minus sign.
		String normalised = trimmed.toUpperCase(java.util.Locale.ROOT).replace(" ", "")
				.replace("POSITIVE", "+").replace("NEGATIVE", "-")
				.replace("+VE", "+").replace("-VE", "-")
				.replace("−", "-");
		if (!BLOOD_GROUPS.contains(normalised)) {
			throw new IllegalArgumentException("Blood group must be one of A+, A-, B+, B-, AB+, AB-, O+, O-");
		}
		return normalised;
	}

	static String normalisePhone(String value) {
		String trimmed = blankToNull(value);
		if (trimmed == null) {
			return null;
		}
		String normalised = trimmed.replaceAll("[\\s()-]", "");
		if (!PHONE.matcher(normalised).matches()) {
			throw new IllegalArgumentException("Emergency phone must be 7-15 digits, optionally starting with +");
		}
		return normalised;
	}

	// ---------------------------------------------------------------- helpers

	private UUID requireEditable(IdCardOwnerType type, UUID ownerId) {
		return type == IdCardOwnerType.STUDENT
				? requireStudent(ownerId, Access.EDIT).getSchoolId()
				: requireEmployee(ownerId, Access.EDIT).getSchoolId();
	}

	private boolean canEditStudent(UUID studentId) {
		AuthPrincipal principal = AuthContext.current();
		// A PARENT reaching this point has already passed requireLinkedChild.
		return principal.getRole() == Role.PARENT
				|| (principal.getRole() == Role.STUDENT && principal.getOwnerId().equals(studentId));
	}

	private boolean canEditEmployee(UUID employeeId) {
		AuthPrincipal principal = AuthContext.current();
		return principal.getOwnerType() == OwnerType.EMPLOYEE && principal.getOwnerId().equals(employeeId);
	}

	public IdCardProfile profile(UUID schoolId, IdCardOwnerType type, UUID ownerId) {
		return profileRepository.findBySchoolIdAndOwnerTypeAndOwnerId(schoolId, type, ownerId).orElse(null);
	}

	private IdCardProfile editableProfile(UUID schoolId, IdCardOwnerType type, UUID ownerId) {
		IdCardProfile profile = profile(schoolId, type, ownerId);
		if (profile == null) {
			profile = new IdCardProfile();
			profile.setSchoolId(schoolId);
			profile.setOwnerType(type);
			profile.setOwnerId(ownerId);
		}
		profile.setUpdatedBy(AuthContext.current().getUsername());
		return profile;
	}

	public School school(UUID schoolId) {
		return schoolRepository.findById(schoolId).orElseThrow(() -> new EntityNotFoundException("School not found"));
	}

	public static String photoKey(IdCardProfile profile) {
		return profile != null ? profile.getPhotoObjectKey() : null;
	}

	public static String sectionLabel(ClassSection section) {
		return section != null ? section.getClassName() + " - " + section.getSection() : null;
	}

	public static String schoolAddress(School school) {
		return Stream.of(school.getAddress(), school.getCity(), school.getState(), school.getPincode())
				.filter(part -> part != null && !part.isBlank())
				.collect(Collectors.joining(", "));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
