package com.gurukul.chat.service;

import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.chat.dto.AnnouncementDtos.CreateAnnouncementRequest;
import com.gurukul.chat.entity.Announcement;
import com.gurukul.chat.entity.AnnouncementScope;
import com.gurukul.chat.repository.AnnouncementRepository;
import com.gurukul.common.EntityNotFoundException;
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
	private final SimpMessagingTemplate messagingTemplate;

	public AnnouncementService(
			AnnouncementRepository announcementRepository,
			ClassSectionRepository classSectionRepository,
			StudentRepository studentRepository,
			@Lazy SimpMessagingTemplate messagingTemplate) {
		this.announcementRepository = announcementRepository;
		this.classSectionRepository = classSectionRepository;
		this.studentRepository = studentRepository;
		this.messagingTemplate = messagingTemplate;
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
		} else {
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
		}

		Announcement saved = announcementRepository.save(announcement);
		broadcast(saved);
		return saved;
	}

	@Transactional(readOnly = true)
	public List<Announcement> listVisible(AuthPrincipal principal, UUID sectionId) {
		UUID schoolId = principal.getSchoolId();
		List<Announcement> result = new java.util.ArrayList<>(
				announcementRepository.findAllBySchoolIdAndScopeOrderByCreatedAtDesc(schoolId, AnnouncementScope.SCHOOL));

		if (sectionId != null) {
			if (!isSectionVisibleTo(principal, sectionId)) {
				throw new AccessDeniedException("You do not have visibility into this section's announcements");
			}
			result.addAll(announcementRepository.findAllBySectionIdOrderByCreatedAtDesc(sectionId));
		}
		return result;
	}

	/**
	 * School-wide announcements are visible to any authenticated member of the school, by design -
	 * announcements are for the whole community. Class-level: ADMIN, any TEACHER, or a STUDENT
	 * enrolled in that section.
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
		Student student = studentRepository.findByIdAndSchoolId(principal.getOwnerId(), schoolId)
				.orElseThrow(() -> new EntityNotFoundException("Student not found"));
		return student.getClassSection().getId().equals(sectionId);
	}

	public boolean isSchoolVisibleTo(AuthPrincipal principal, UUID schoolId) {
		return principal.getSchoolId().equals(schoolId);
	}

	private void broadcast(Announcement announcement) {
		String destination = announcement.getScope() == AnnouncementScope.SCHOOL
				? "/topic/schools/" + announcement.getSchoolId() + "/announcements"
				: "/topic/sections/" + announcement.getSectionId() + "/announcements";
		messagingTemplate.convertAndSend(destination,
				com.gurukul.chat.dto.AnnouncementDtos.AnnouncementResponse.from(announcement));
	}

}
