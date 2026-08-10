package com.gurukul.exams.service;

import com.gurukul.attendance.dto.AttendanceDtos.StudentAttendanceHistoryResponse;
import com.gurukul.attendance.service.AttendanceService;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthContext;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.employees.service.EmployeeService;
import com.gurukul.exams.dto.ReportCardDtos.PublicationResponse;
import com.gurukul.exams.dto.ReportCardDtos.ReportCardResponse;
import com.gurukul.exams.dto.ReportCardDtos.SubjectResultResponse;
import com.gurukul.exams.entity.AssessmentResult;
import com.gurukul.exams.entity.ReportCardPublication;
import com.gurukul.exams.repository.AssessmentResultRepository;
import com.gurukul.exams.repository.ReportCardPublicationRepository;
import com.gurukul.academics.entity.Subject;
import com.gurukul.notifications.service.PushNotificationService;
import com.gurukul.notifications.service.PushNotificationService.Recipient;
import com.gurukul.parents.service.ParentService;
import com.gurukul.students.entity.ClassSection;
import com.gurukul.students.entity.Student;
import com.gurukul.students.repository.StudentRepository;
import com.gurukul.students.service.ClassSectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportCardService {

	private final ReportCardPublicationRepository reportCardPublicationRepository;
	private final AssessmentResultRepository assessmentResultRepository;
	private final StudentRepository studentRepository;
	private final ClassSectionService classSectionService;
	private final EmployeeService employeeService;
	private final GradingScaleService gradingScaleService;
	private final AttendanceService attendanceService;
	private final SchoolContext schoolContext;
	private final PushNotificationService pushNotificationService;
	private final ParentService parentService;

	@Transactional
	public PublicationResponse publish(UUID sectionId, String term) {
		AuthPrincipal principal = AuthContext.current();
		ClassSection section = classSectionService.getScopedClassSection(sectionId);
		boolean isClassTeacher = principal.getRole() == Role.TEACHER
				&& section.getClassTeacher() != null
				&& section.getClassTeacher().getId().equals(principal.getOwnerId());
		if (principal.getRole() != Role.ADMIN && !isClassTeacher) {
			throw new AccessDeniedException("Only an admin or this section's class teacher can publish report cards");
		}
		ReportCardPublication publication = reportCardPublicationRepository.findByClassSection_IdAndTerm(sectionId, term)
				.orElseGet(ReportCardPublication::new);
		publication.setSchoolId(schoolContext.getSchoolId());
		publication.setClassSection(section);
		publication.setTerm(term);
		publication.setPublishedAt(Instant.now());
		publication.setPublishedByEmployee(employeeService.getScopedEntity(principal.getOwnerId()));
		ReportCardPublication saved = reportCardPublicationRepository.save(publication);
		notifyStudents(saved);
		return new PublicationResponse(
				saved.getClassSection().getId(), saved.getTerm(), saved.getPublishedAt(), saved.getPublishedByEmployee().getName());
	}

	private void notifyStudents(ReportCardPublication publication) {
		UUID sectionId = publication.getClassSection().getId();
		List<Recipient> recipients = studentRepository.findAllBySchoolIdAndClassSectionId(publication.getSchoolId(), sectionId).stream()
				.map(s -> new Recipient(OwnerType.STUDENT, s.getId()))
				.toList();
		pushNotificationService.sendToRecipients(publication.getSchoolId(), recipients, "Report card published",
				"Your " + publication.getTerm() + " report card is now available",
				Map.of("type", "REPORT_CARD_PUBLISHED", "sectionId", String.valueOf(sectionId), "term", publication.getTerm()));
	}

	/**
	 * A STUDENT session may only see this once the section's term is published; an ADMIN/TEACHER
	 * can preview it any time (published or not) so a principal can review before publishing.
	 */
	@Transactional(readOnly = true)
	public ReportCardResponse getReportCard(UUID studentId, String term) {
		AuthPrincipal principal = AuthContext.current();
		if (principal.getRole() == Role.STUDENT && !principal.getOwnerId().equals(studentId)) {
			throw new AccessDeniedException("Students can only view their own report card");
		}
		if (principal.getRole() == Role.PARENT) {
			parentService.requireLinkedChild(principal.getOwnerId(), studentId, principal.getSchoolId());
		}

		UUID schoolId = schoolContext.getSchoolId();
		Student student = studentRepository.findByIdAndSchoolId(studentId, schoolId)
				.orElseThrow(() -> new EntityNotFoundException("Student not found"));
		ClassSection section = student.getClassSection();
		if (section == null) {
			throw new IllegalStateException("Student is not currently assigned to a class-section");
		}

		boolean published = reportCardPublicationRepository.existsByClassSection_IdAndTerm(section.getId(), term);
		if (!published && (principal.getRole() == Role.STUDENT || principal.getRole() == Role.PARENT)) {
			throw new IllegalStateException("The report card for " + term + " has not been published yet");
		}

		List<AssessmentResult> results = assessmentResultRepository
				.findAllBySchoolIdAndStudentIdAndAssessment_Section_IdAndAssessment_Term(schoolId, studentId, section.getId(), term);

		Map<UUID, List<AssessmentResult>> bySubject = results.stream()
				.filter(r -> r.getAssessment().getSubject() != null)
				.collect(Collectors.groupingBy(r -> r.getAssessment().getSubject().getId()));

		List<SubjectResultResponse> subjectRows = bySubject.values().stream()
				.map(this::toSubjectRow)
				.sorted(Comparator.comparing(SubjectResultResponse::getSubjectName))
				.toList();

		BigDecimal totalMax = subjectRows.stream().map(SubjectResultResponse::getMaxMarks).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal totalObtained = subjectRows.stream().map(SubjectResultResponse::getMarksObtained).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal overallPercentage = percentageOf(totalObtained, totalMax);
		String overallGrade = gradingScaleService.resolveGrade(overallPercentage);

		StudentAttendanceHistoryResponse attendance = attendanceService.getStudentHistory(studentId, null, null);
		BigDecimal attendancePercentage = attendance.getTotalRecords() > 0
				? percentageOf(BigDecimal.valueOf(attendance.getPresentCount()), BigDecimal.valueOf(attendance.getTotalRecords()))
				: null;

		Instant publishedAt = published
				? reportCardPublicationRepository.findByClassSection_IdAndTerm(section.getId(), term)
						.map(ReportCardPublication::getPublishedAt).orElse(null)
				: null;

		return new ReportCardResponse(
				student.getId(), student.getName(), student.getRollNumber(),
				section.getClassName(), section.getSection(), section.getAcademicYear(), term,
				subjectRows, totalMax, totalObtained, overallPercentage, overallGrade,
				attendancePercentage, published, publishedAt
		);
	}

	private SubjectResultResponse toSubjectRow(List<AssessmentResult> resultsForSubject) {
		Subject subject = resultsForSubject.get(0).getAssessment().getSubject();
		BigDecimal maxMarks = resultsForSubject.stream()
				.map(r -> r.getAssessment().getMaxMarks()).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal marksObtained = resultsForSubject.stream()
				.map(r -> r.isAbsent() || r.getMarksObtained() == null ? BigDecimal.ZERO : r.getMarksObtained())
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal percentage = percentageOf(marksObtained, maxMarks);
		return new SubjectResultResponse(
				subject.getId(), subject.getName(), subject.getCode(), maxMarks, marksObtained, percentage,
				gradingScaleService.resolveGrade(percentage));
	}

	private BigDecimal percentageOf(BigDecimal obtained, BigDecimal max) {
		if (max.compareTo(BigDecimal.ZERO) <= 0) {
			return BigDecimal.ZERO;
		}
		return obtained.multiply(BigDecimal.valueOf(100)).divide(max, 2, RoundingMode.HALF_UP);
	}

}
