package com.gurukul.admissions.service;

import com.gurukul.admissions.dto.AdmissionDtos.AdmissionRequest;
import com.gurukul.admissions.dto.AdmissionDtos.AdmissionResponse;
import com.gurukul.admissions.dto.AdmissionDtos.ConvertRequest;
import com.gurukul.admissions.dto.AdmissionDtos.ConvertResponse;
import com.gurukul.admissions.dto.AdmissionDtos.DocumentResponse;
import com.gurukul.admissions.dto.AdmissionDtos.DuplicateStudent;
import com.gurukul.admissions.dto.AdmissionDtos.PresignDocumentRequest;
import com.gurukul.admissions.dto.AdmissionDtos.PresignDocumentResponse;
import com.gurukul.admissions.dto.AdmissionDtos.RegisterDocumentRequest;
import com.gurukul.admissions.entity.AdmissionApplication;
import com.gurukul.admissions.entity.AdmissionDocument;
import com.gurukul.admissions.entity.AdmissionStage;
import com.gurukul.admissions.repository.AdmissionApplicationRepository;
import com.gurukul.admissions.repository.AdmissionDocumentRepository;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthContext;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.common.storage.S3PresignHelper;
import com.gurukul.registration.dto.RegistrationDtos.StudentInviteResponse;
import com.gurukul.registration.service.StudentInviteService;
import com.gurukul.students.dto.StudentRequest;
import com.gurukul.students.entity.ClassSection;
import com.gurukul.students.entity.Student;
import com.gurukul.students.repository.StudentRepository;
import com.gurukul.students.service.ClassSectionService;
import com.gurukul.students.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin-only admission applications for the current school. Enrolment (convert) goes through
 * StudentService.createEntity - the same path as POST /students - so registration number, roll
 * number and the fee assessment are produced exactly as for any other new student.
 */
@Service
@RequiredArgsConstructor
public class AdmissionService {

	private final AdmissionApplicationRepository applicationRepository;
	private final AdmissionDocumentRepository documentRepository;
	private final SchoolContext schoolContext;
	private final ClassSectionService classSectionService;
	private final StudentService studentService;
	private final StudentRepository studentRepository;
	private final StudentInviteService studentInviteService;
	private final S3PresignHelper presignHelper;

	@Transactional(readOnly = true)
	public List<AdmissionResponse> list(AdmissionStage stage) {
		requireAdmin();
		UUID schoolId = schoolContext.getSchoolId();
		List<AdmissionApplication> rows = stage == null
				? applicationRepository.findAllBySchoolIdOrderByCreatedAtDesc(schoolId)
				: applicationRepository.findAllBySchoolIdAndStageOrderByCreatedAtDesc(schoolId, stage);
		return rows.stream().map(AdmissionResponse::summary).toList();
	}

	@Transactional(readOnly = true)
	public AdmissionResponse get(UUID id) {
		requireAdmin();
		return toDetail(findScoped(id));
	}

	@Transactional
	public AdmissionResponse create(AdmissionRequest request) {
		requireAdmin();
		AdmissionApplication application = new AdmissionApplication();
		application.setSchoolId(schoolContext.getSchoolId());
		application.setStage(AdmissionStage.NEW);
		apply(application, request);
		return toDetail(applicationRepository.save(application));
	}

	@Transactional
	public AdmissionResponse update(UUID id, AdmissionRequest request) {
		requireAdmin();
		AdmissionApplication application = findScoped(id);
		requireNotEnrolled(application);
		apply(application, request);
		return toDetail(applicationRepository.save(application));
	}

	@Transactional
	public void delete(UUID id) {
		requireAdmin();
		AdmissionApplication application = findScoped(id);
		requireNotEnrolled(application);
		documentRepository.deleteAllByApplicationId(application.getId());
		applicationRepository.delete(application);
	}

	@Transactional
	public AdmissionResponse changeStage(UUID id, AdmissionStage target) {
		requireAdmin();
		AdmissionApplication application = findScoped(id);
		if (target == AdmissionStage.ENROLLED) {
			throw new IllegalArgumentException("Use enrol (POST /api/v1/admissions/{id}/convert) to enrol an application");
		}
		if (!application.getStage().canMoveTo(target)) {
			throw new IllegalStateException(
					"Cannot move an application from " + application.getStage() + " to " + target);
		}
		application.setStage(target);
		application.setDecidedAt(target == AdmissionStage.APPROVED || target == AdmissionStage.REJECTED
				? Instant.now() : null);
		return toDetail(applicationRepository.save(application));
	}

	@Transactional
	public ConvertResponse convert(UUID id, ConvertRequest request) {
		AuthPrincipal principal = requireAdmin();
		UUID schoolId = schoolContext.getSchoolId();
		// Locked for the rest of the transaction: a concurrent second enrol waits here, then takes the
		// already-enrolled branch below instead of creating a second student (and a second fee bill).
		AdmissionApplication application = applicationRepository.findForUpdate(id, schoolId)
				.orElseThrow(() -> new EntityNotFoundException("Admission application not found"));

		if (application.getStage() == AdmissionStage.ENROLLED) {
			Student existing = application.getStudentId() == null ? null
					: studentRepository.findByIdAndSchoolId(application.getStudentId(), schoolId).orElse(null);
			return toConvertResponse(application, existing, true, null);
		}
		if (application.getStage() != AdmissionStage.APPROVED) {
			throw new IllegalStateException("Only an approved application can be enrolled");
		}

		ClassSection classSection = classSectionService.getScopedClassSection(request.getClassSectionId());
		if (!classSection.getClassName().equalsIgnoreCase(application.getAppliedClassName())) {
			throw new IllegalArgumentException("Pick a section of " + application.getAppliedClassName()
					+ " - the class this application is for");
		}

		List<DuplicateStudent> duplicates = findPossibleDuplicates(application);
		if (!duplicates.isEmpty() && !request.isAllowDuplicate()) {
			String matches = duplicates.stream()
					.map(d -> d.getName() + ", " + d.getClassSectionLabel())
					.collect(Collectors.joining("; "));
			throw new AdmissionDuplicateException("A student with the same name, date of birth and parent phone "
					+ "already exists (" + matches + "). Check it is not the same child, then confirm to enrol anyway.");
		}

		LocalDate admissionDate = request.getAdmissionDate() != null ? request.getAdmissionDate() : LocalDate.now();
		if (admissionDate.isBefore(application.getDob())) {
			throw new IllegalArgumentException("Admission date can't be before the date of birth");
		}

		StudentRequest studentRequest = new StudentRequest();
		studentRequest.setName(application.getStudentName());
		studentRequest.setDob(application.getDob());
		studentRequest.setGender(application.getGender());
		studentRequest.setAddress(application.getAddress());
		studentRequest.setParentName(application.getParentName());
		studentRequest.setParentContact(application.getParentContact());
		studentRequest.setClassSectionId(classSection.getId());
		studentRequest.setAdmissionDate(admissionDate);
		studentRequest.setPreviousSchoolName(application.getPreviousSchoolName());
		// Same path as POST /students: registration number, roll-number recompute, fee assessment.
		Student student = studentService.createEntity(studentRequest);

		application.setStage(AdmissionStage.ENROLLED);
		application.setStudentId(student.getId());
		application.setAssignedClassSectionId(classSection.getId());
		application.setEnrolledAt(Instant.now());
		applicationRepository.saveAndFlush(application);

		StudentInviteResponse invite = request.isSendParentInvite()
				? studentInviteService.createInviteForStudent(principal, student.getId())
				: null;
		return toConvertResponse(application, student, false, invite);
	}

	@Transactional(readOnly = true)
	public PresignDocumentResponse presignDocument(UUID id, PresignDocumentRequest request) {
		requireAdmin();
		AdmissionApplication application = findScoped(id);
		requireStorageConfigured();
		presignHelper.validateUpload(request.getContentType(), request.getFileSizeBytes());
		String objectKey = documentKeyPrefix(application) + UUID.randomUUID() + "-"
				+ S3PresignHelper.sanitizeFileName(request.getFileName());
		S3PresignHelper.PresignedUpload presigned =
				presignHelper.presignUpload(objectKey, request.getContentType(), request.getFileSizeBytes());
		return new PresignDocumentResponse(presigned.uploadUrl(), presigned.objectKey(), presigned.expiresAt());
	}

	@Transactional
	public AdmissionResponse registerDocument(UUID id, RegisterDocumentRequest request) {
		requireAdmin();
		AdmissionApplication application = findScoped(id);
		requireStorageConfigured();
		presignHelper.validateUpload(request.getContentType(), request.getFileSizeBytes());
		// Only keys this service handed out for this application - never an arbitrary bucket path.
		String objectKey = request.getObjectKey();
		if (!objectKey.startsWith(documentKeyPrefix(application)) || objectKey.contains("..")) {
			throw new IllegalArgumentException("That upload doesn't belong to this application");
		}
		if (!documentRepository.existsByObjectKey(objectKey)) {
			AdmissionDocument document = new AdmissionDocument();
			document.setSchoolId(application.getSchoolId());
			document.setApplicationId(application.getId());
			document.setDocumentType(request.getDocumentType());
			document.setObjectKey(objectKey);
			document.setFileName(request.getFileName());
			document.setContentType(request.getContentType());
			document.setFileSizeBytes(request.getFileSizeBytes());
			documentRepository.save(document);
		}
		return toDetail(application);
	}

	@Transactional
	public AdmissionResponse deleteDocument(UUID id, UUID documentId) {
		requireAdmin();
		AdmissionApplication application = findScoped(id);
		AdmissionDocument document = documentRepository
				.findByIdAndApplicationIdAndSchoolId(documentId, application.getId(), application.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Document not found"));
		documentRepository.delete(document);
		documentRepository.flush();
		return toDetail(application);
	}

	private String documentKeyPrefix(AdmissionApplication application) {
		return "admissions/%s/%s/".formatted(application.getSchoolId(), application.getId());
	}

	private void requireStorageConfigured() {
		if (!presignHelper.isConfigured()) {
			throw new IllegalStateException("Document uploads are not configured on this server");
		}
	}

	private AdmissionResponse toDetail(AdmissionApplication application) {
		boolean storage = presignHelper.isConfigured();
		List<DocumentResponse> documents = documentRepository.findAllByApplicationIdOrderByCreatedAtAsc(application.getId())
				.stream()
				.map(d -> DocumentResponse.from(d, storage ? presignHelper.presignDownload(d.getObjectKey()) : null))
				.toList();
		List<DuplicateStudent> duplicates = application.getStage() == AdmissionStage.ENROLLED
				? List.of()
				: findPossibleDuplicates(application);
		return AdmissionResponse.detail(application, documents, duplicates, storage);
	}

	/** Same child already on the roll? Matches name (case/spacing-insensitive) + DOB + parent phone. */
	private List<DuplicateStudent> findPossibleDuplicates(AdmissionApplication application) {
		String name = normalizeName(application.getStudentName());
		return studentRepository.findAllBySchoolIdAndParentContact(application.getSchoolId(), application.getParentContact())
				.stream()
				.filter(s -> application.getDob().equals(s.getDob()))
				.filter(s -> name.equals(normalizeName(s.getName())))
				.filter(s -> !s.getId().equals(application.getStudentId()))
				.map(s -> new DuplicateStudent(s.getId(), s.getName(), s.getRollNumber(), s.getClassSection().getDisplayLabel()))
				.toList();
	}

	private static String normalizeName(String name) {
		return name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	private ConvertResponse toConvertResponse(AdmissionApplication application, Student student, boolean alreadyEnrolled,
			StudentInviteResponse invite) {
		return new ConvertResponse(
				toDetail(application),
				student != null ? student.getId() : application.getStudentId(),
				student != null ? student.getName() : null,
				student != null ? student.getRollNumber() : null,
				student != null ? student.getRegistrationNumber() : null,
				student != null ? student.getClassSection().getDisplayLabel() : null,
				alreadyEnrolled,
				invite != null ? invite.getCode() : null,
				invite != null ? invite.getExpiresAt() : null);
	}

	private void apply(AdmissionApplication application, AdmissionRequest request) {
		application.setStudentName(request.getStudentName().trim());
		application.setDob(request.getDob());
		application.setGender(request.getGender());
		application.setAddress(request.getAddress().trim());
		application.setPreviousSchoolName(blankToNull(request.getPreviousSchoolName()));
		application.setParentName(request.getParentName().trim());
		application.setParentContact(request.getParentContact().trim());
		application.setParentEmail(blankToNull(request.getParentEmail()));
		application.setAppliedClassName(resolveClassName(request.getAppliedClassName()));
		application.setNotes(blankToNull(request.getNotes()));
	}

	/** The class applied for must be one the school actually has, stored with the school's own spelling. */
	private String resolveClassName(String requested) {
		String wanted = requested.trim();
		return classSectionService.listClassNames().stream()
				.filter(name -> name.equalsIgnoreCase(wanted))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No class named \"" + wanted + "\" in this school"));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private void requireNotEnrolled(AdmissionApplication application) {
		if (application.getStage() == AdmissionStage.ENROLLED) {
			throw new IllegalStateException("This application is already enrolled - edit the student record instead");
		}
	}

	private AdmissionApplication findScoped(UUID id) {
		return applicationRepository.findByIdAndSchoolId(id, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Admission application not found"));
	}

	private AuthPrincipal requireAdmin() {
		AuthPrincipal principal = AuthContext.current();
		if (principal.getRole() != Role.ADMIN) {
			throw new AccessDeniedException("Only an admin can manage admissions");
		}
		return principal;
	}

}
