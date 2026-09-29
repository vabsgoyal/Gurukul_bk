package com.gurukul.chat.service;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.chat.dto.AnnouncementDtos.CreateAnnouncementRequest;
import com.gurukul.chat.entity.Announcement;
import com.gurukul.chat.entity.AnnouncementScope;
import com.gurukul.chat.repository.AnnouncementRepository;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.notifications.service.PushChannel;
import com.gurukul.notifications.service.PushNotificationService;
import com.gurukul.notifications.service.PushNotificationService.Recipient;
import com.gurukul.parents.repository.ParentStudentLinkRepository;
import com.gurukul.students.entity.ClassSection;
import com.gurukul.students.entity.Student;
import com.gurukul.students.repository.ClassSectionRepository;
import com.gurukul.students.repository.StudentRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Takes an explicit AuthPrincipal, never AuthContext.current()/SchoolContext - see ConversationService.
 * The visibility rule here ({@link #isSectionVisibleTo}) is shared with
 * StompSubscribeAuthorizationInterceptor so REST history and live WS delivery never disagree.
 *
 * <p>SimpMessagingTemplate is injected {@code @Lazy}: WebSocketConfig's DelegatingWebSocketMessageBrokerConfiguration
 * builds the broker's SimpMessagingTemplate bean by first constructing every WebSocketMessageBrokerConfigurer
 * (including WebSocketConfig, which needs StompSubscribeAuthorizationInterceptor, which needs this service) -
 * eagerly resolving SimpMessagingTemplate here would create a circular dependency back to that same bean.
 */
@Service
public class AnnouncementService {

	private final AnnouncementRepository announcementRepository;
	private final ClassSectionRepository classSectionRepository;
	private final StudentRepository studentRepository;
	private final EmployeeRepository employeeRepository;
	private final SimpMessagingTemplate messagingTemplate;
	private final PushNotificationService pushNotificationService;
	private final ParentStudentLinkRepository parentStudentLinkRepository;

	public AnnouncementService(
			AnnouncementRepository announcementRepository,
			ClassSectionRepository classSectionRepository,
			StudentRepository studentRepository,
			EmployeeRepository employeeRepository,
			@Lazy SimpMessagingTemplate messagingTemplate,
			PushNotificationService pushNotificationService,
			ParentStudentLinkRepository parentStudentLinkRepository) {
		this.announcementRepository = announcementRepository;
		this.classSectionRepository = classSectionRepository;
		this.studentRepository = studentRepository;
		this.employeeRepository = employeeRepository;
		this.messagingTemplate = messagingTemplate;
		this.pushNotificationService = pushNotificationService;
		this.parentStudentLinkRepository = parentStudentLinkRepository;
	}

	@Transactional
	public Announcement create(AuthPrincipal principal, CreateAnnouncementRequest request) {
		UUID schoolId = principal.getSchoolId();

		Announcement announcement = new Announcement();
		announcement.setSchoolId(schoolId);
		announcement.setScope(request.getScope());
		announcement.setAuthorEmployeeId(principal.getOwnerId());
		announcement.setTitle(request.getTitle());
		announcement.setBody(request.getBody());

		if (request.getScope() == AnnouncementScope.SCHOOL) {
			if (principal.getRole() != Role.ADMIN) {
				throw new AccessDeniedException("Only an admin can post a school-wide announcement");
			}
			announcement.setSectionId(null);
		} else if (request.getScope() == AnnouncementScope.CLASS) {
			if (request.getSectionId() == null) {
				throw new IllegalArgumentException("sectionId is required for a class-level announcement");
			}
			ClassSection section = classSectionRepository.findByIdAndSchoolId(request.getSectionId(), schoolId)
					.orElseThrow(() -> new EntityNotFoundException("Class-section not found"));
			boolean isClassTeacher = section.getClassTeacher() != null
					&& section.getClassTeacher().getId().equals(principal.getOwnerId());
			if (principal.getRole() != Role.ADMIN && !(principal.getRole() == Role.TEACHER && isClassTeacher)) {
				throw new AccessDeniedException(
						"Only an admin or this section's class teacher can post a class-level announcement");
			}
			announcement.setSectionId(section.getId());
		} else {
			if (request.getClassName() == null || request.getClassName().isBlank()) {
				throw new IllegalArgumentException("className is required for a grade-level announcement");
			}
			List<ClassSection> sections =
					classSectionRepository.findAllBySchoolIdAndClassNameOrderBySectionAsc(schoolId, request.getClassName());
			if (sections.isEmpty()) {
				throw new EntityNotFoundException("No sections found for that class");
			}
			boolean isClassTeacherOfGrade = sections.stream().anyMatch(s ->
					s.getClassTeacher() != null && s.getClassTeacher().getId().equals(principal.getOwnerId()));
			if (principal.getRole() != Role.ADMIN && !(principal.getRole() == Role.TEACHER && isClassTeacherOfGrade)) {
				throw new AccessDeniedException(
						"Only an admin or one of this grade's class teachers can post a grade-level announcement");
			}
			announcement.setClassName(request.getClassName());
		}

		Announcement saved = announcementRepository.save(announcement);
		broadcast(saved);
		notify(saved);
		return saved;
	}

	@Transactional(readOnly = true)
	public List<Announcement> listVisible(AuthPrincipal principal, UUID sectionId, String className) {
		UUID schoolId = principal.getSchoolId();
		List<Announcement> result = new java.util.ArrayList<>(
				announcementRepository.findAllBySchoolIdAndScopeOrderByCreatedAtDesc(schoolId, AnnouncementScope.SCHOOL));

		if (sectionId != null) {
			if (!isSectionVisibleTo(principal, sectionId)) {
				throw new AccessDeniedException("You do not have visibility into this section's announcements");
			}
			result.addAll(announcementRepository.findAllBySectionIdOrderByCreatedAtDesc(sectionId));
		}
		if (className != null && !className.isBlank()) {
			if (!isGradeVisibleTo(principal, className)) {
				throw new AccessDeniedException("You do not have visibility into this grade's announcements");
			}
			result.addAll(announcementRepository.findAllBySchoolIdAndClassNameOrderByCreatedAtDesc(schoolId, className));
		}
		return result;
	}

	/**
	 * School-wide announcements are visible to any authenticated member of the school, by design -
	 * announcements are for the whole community. Class-level: ADMIN, any TEACHER, a STUDENT enrolled
	 * in that section, or a PARENT with a linked child enrolled in it.
	 */
	@Transactional(readOnly = true)
	public boolean isSectionVisibleTo(AuthPrincipal principal, UUID sectionId) {
		UUID schoolId = principal.getSchoolId();
		if (!classSectionRepository.findByIdAndSchoolId(sectionId, schoolId).isPresent()) {
			throw new EntityNotFoundException("Class-section not found");
		}
		if (principal.getRole() == Role.ADMIN || principal.getRole() == Role.TEACHER) {
			return true;
		}
		if (principal.getRole() == Role.PARENT) {
			return linkedChildren(principal).stream().anyMatch(c -> c.getClassSection().getId().equals(sectionId));
		}
		Student student = studentRepository.findByIdAndSchoolId(principal.getOwnerId(), schoolId)
				.orElseThrow(() -> new EntityNotFoundException("Student not found"));
		return student.getClassSection().getId().equals(sectionId);
	}

	/** The parent's linked children at their own school (links are per school already; the school filter is belt-and-braces). */
	private List<Student> linkedChildren(AuthPrincipal parent) {
		List<UUID> childIds = parentStudentLinkRepository.findAllByParentId(parent.getOwnerId()).stream()
				.map(link -> link.getStudentId())
				.toList();
		return childIds.isEmpty() ? List.of() : studentRepository.findAllBySchoolIdAndIdIn(parent.getSchoolId(), childIds);
	}

	public boolean isSchoolVisibleTo(AuthPrincipal principal, UUID schoolId) {
		return principal.getSchoolId().equals(schoolId);
	}

	/**
	 * Grade-level: ADMIN, any TEACHER, a STUDENT currently enrolled in any section of that className,
	 * or a PARENT with a linked child who is. Doesn't distinguish academic year - Student only tracks
	 * one current enrollment, so "which grade am I in right now" is all that matters for this
	 * live-visibility check.
	 */
	@Transactional(readOnly = true)
	public boolean isGradeVisibleTo(AuthPrincipal principal, String className) {
		if (principal.getRole() == Role.ADMIN || principal.getRole() == Role.TEACHER) {
			return true;
		}
		if (principal.getRole() == Role.PARENT) {
			return linkedChildren(principal).stream().anyMatch(c -> c.getClassSection().getClassName().equals(className));
		}
		Student student = studentRepository.findByIdAndSchoolId(principal.getOwnerId(), principal.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Student not found"));
		return student.getClassSection().getClassName().equals(className);
	}

	/**
	 * Topic path segments can't contain spaces or "/". Deliberately NOT java.net.URLEncoder: it
	 * encodes space as "+" (application/x-www-form-urlencoded rules), which silently mismatches
	 * any client that percent-encodes spaces as "%20" (the normal URI convention) - the STOMP
	 * broker matches destinations by exact string, so that mismatch means a subscriber's messages
	 * would just never arrive, with no error on either side. A plain, predictable substitution
	 * avoids the ambiguity entirely; class names in this system (e.g. "Grade 8") don't contain
	 * underscores in practice.
	 */
	public static String encodeClassNameForTopic(String className) {
		return className.replace(" ", "_");
	}

	public static String decodeClassNameFromTopic(String encoded) {
		return encoded.replace("_", " ");
	}

	/**
	 * Every employee (any ADMIN/TEACHER can see any announcement - see isSectionVisibleTo/
	 * isGradeVisibleTo above), whichever students the scope actually covers, and those students'
	 * parents. The push carries the announcement's own text, so it reads in full even from the
	 * notification shade; parents can also re-read it on the app's Announcements screen.
	 */
	private void notify(Announcement announcement) {
		UUID schoolId = announcement.getSchoolId();
		List<Recipient> recipients = new java.util.ArrayList<>(
				employeeRepository.findAllBySchoolIdOrderByNameAsc(schoolId).stream()
						.map(e -> new Recipient(OwnerType.EMPLOYEE, e.getId()))
						.toList());

		List<Student> students = switch (announcement.getScope()) {
			case SCHOOL -> studentRepository.findAllBySchoolId(schoolId);
			case CLASS -> studentRepository.findAllBySchoolIdAndClassSectionId(schoolId, announcement.getSectionId());
			case GRADE -> studentRepository.findAllBySchoolIdAndClassSection_ClassName(schoolId, announcement.getClassName());
		};
		students.forEach(s -> recipients.add(new Recipient(OwnerType.STUDENT, s.getId())));
		if (!students.isEmpty()) {
			parentStudentLinkRepository.findAllBySchoolIdAndStudentIdIn(schoolId, students.stream().map(Student::getId).toList())
					.forEach(link -> recipients.add(new Recipient(OwnerType.PARENT, link.getParentId())));
		}

		pushNotificationService.sendToRecipients(schoolId, recipients, PushChannel.ANNOUNCEMENTS,
				announcement.getTitle(), pushBody(announcement.getBody()),
				Map.of("type", "ANNOUNCEMENT", "announcementId", String.valueOf(announcement.getId())));
	}

	static final int PUSH_BODY_MAX = 150;

	/** Android shows about two lines collapsed; a long notice is cut at a word, not mid-word. */
	static String pushBody(String body) {
		if (body == null || body.isBlank()) {
			return "Tap to open Smart Gurukul.";
		}
		String text = body.strip().replaceAll("\\s+", " ");
		if (text.length() <= PUSH_BODY_MAX) {
			return text;
		}
		int cut = text.lastIndexOf(' ', PUSH_BODY_MAX - 1);
		return text.substring(0, cut > PUSH_BODY_MAX / 2 ? cut : PUSH_BODY_MAX - 1).stripTrailing() + "…";
	}

	private void broadcast(Announcement announcement) {
		String destination;
		if (announcement.getScope() == AnnouncementScope.SCHOOL) {
			destination = "/topic/schools/" + announcement.getSchoolId() + "/announcements";
		} else if (announcement.getScope() == AnnouncementScope.CLASS) {
			destination = "/topic/sections/" + announcement.getSectionId() + "/announcements";
		} else {
			destination = "/topic/schools/" + announcement.getSchoolId() + "/classes/"
					+ encodeClassNameForTopic(announcement.getClassName()) + "/announcements";
		}
		messagingTemplate.convertAndSend(destination,
				com.gurukul.chat.dto.AnnouncementDtos.AnnouncementResponse.from(announcement));
	}

}
