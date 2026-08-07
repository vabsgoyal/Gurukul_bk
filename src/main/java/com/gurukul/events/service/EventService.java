package com.gurukul.events.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.chat.dto.AnnouncementDtos.CreateAnnouncementRequest;
import com.gurukul.chat.entity.AnnouncementScope;
import com.gurukul.chat.service.AnnouncementService;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.employees.service.EmployeeService;
import com.gurukul.events.dto.EventDtos.CreatePollOptionsRequest;
import com.gurukul.events.dto.EventDtos.PollOptionResult;
import com.gurukul.events.dto.EventDtos.PollResponse;
import com.gurukul.events.dto.EventDtos.RegistrationEntry;
import com.gurukul.events.dto.EventDtos.RsvpRequest;
import com.gurukul.events.dto.EventDtos.RsvpRosterEntry;
import com.gurukul.events.dto.EventDtos.SubmitRegistrationRequest;
import com.gurukul.events.dto.EventDtos.VoteRequest;
import com.gurukul.events.dto.EventRequest;
import com.gurukul.events.dto.EventRequest.RegistrationFieldDefinition;
import com.gurukul.events.dto.EventResponse;
import com.gurukul.events.entity.EventParticipationStatus;
import com.gurukul.events.entity.EventParticipationType;
import com.gurukul.events.entity.EventPollOption;
import com.gurukul.events.entity.EventPollVote;
import com.gurukul.events.entity.EventRegistration;
import com.gurukul.events.entity.EventRsvp;
import com.gurukul.events.entity.EventScope;
import com.gurukul.events.entity.EventStatus;
import com.gurukul.events.entity.SchoolEvent;
import com.gurukul.events.repository.EventPollOptionRepository;
import com.gurukul.events.repository.EventPollVoteRepository;
import com.gurukul.events.repository.EventRegistrationRepository;
import com.gurukul.events.repository.EventRepository;
import com.gurukul.events.repository.EventRsvpRepository;
import com.gurukul.students.entity.ClassSection;
import com.gurukul.students.entity.Student;
import com.gurukul.students.repository.ClassSectionRepository;
import com.gurukul.students.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * SchoolEvent originally existed purely for finance (collections/expenses gating via
 * inflowEnabled/outflowEnabled - see EventCollectionService/EventExpenseService). The
 * participation fields (category/scope/startAt/endAt/participationType/...) and everything below
 * extend that same entity rather than introducing a second "event" concept, so a Sports Meet can
 * both take RSVPs and collect an entry fee through the existing money features.
 *
 * <p>A plain finance-tracking event (participationType left null/NONE) behaves exactly as before -
 * no permission check, no visibility restriction, no notification. Only events that opt into a
 * participation model get the new authorization/visibility/notification behavior.
 */
@Service
@RequiredArgsConstructor
public class EventService {

	private final EventRepository eventRepository;
	private final EventRsvpRepository eventRsvpRepository;
	private final EventRegistrationRepository eventRegistrationRepository;
	private final EventPollOptionRepository eventPollOptionRepository;
	private final EventPollVoteRepository eventPollVoteRepository;
	private final ClassSectionRepository classSectionRepository;
	private final StudentRepository studentRepository;
	private final EmployeeRepository employeeRepository;
	private final EmployeeService employeeService;
	private final AnnouncementService announcementService;
	private final SchoolContext schoolContext;
	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * Visibility-filtered (legacy scope-less finance events stay visible to everyone; scoped
	 * participation events respect CLASS/GRADE visibility) and optionally scope/status-filtered.
	 * principal is null for callers with no JWT (the original, pre-participation finance clients) -
	 * those see everything unfiltered, exactly as before this feature existed.
	 */
	@Transactional(readOnly = true)
	public List<EventResponse> listVisible(AuthPrincipal principal, EventScope scopeFilter, EventParticipationStatus statusFilter) {
		UUID schoolId = principal != null ? principal.getSchoolId() : schoolContext.getSchoolId();
		List<SchoolEvent> all = eventRepository.findAllBySchoolIdOrderByEventDateDesc(schoolId);
		return all.stream()
				.filter(e -> isVisibleTo(principal, e))
				.map(this::toResponse)
				.filter(r -> scopeFilter == null || r.getScope() == scopeFilter)
				.filter(r -> statusFilter == null || r.getParticipationStatus() == statusFilter)
				.toList();
	}

	@Transactional(readOnly = true)
	public EventResponse getById(AuthPrincipal principal, UUID id) {
		return toResponse(requireVisible(principal, id));
	}

	@Transactional
	public EventResponse create(AuthPrincipal principal, EventRequest request) {
		SchoolEvent event = new SchoolEvent();
		event.setSchoolId(schoolContext.getSchoolId());
		applyRequest(event, request);
		event.setStatus(request.getStatus() != null ? request.getStatus() : EventStatus.DRAFT);
		event.setInflowEnabled(Boolean.TRUE.equals(request.getInflowEnabled()));
		event.setOutflowEnabled(Boolean.TRUE.equals(request.getOutflowEnabled()));
		event.setCategory(request.getCategory());
		event.setVenue(request.getVenue());
		event.setStartAt(request.getStartAt());
		event.setEndAt(request.getEndAt());
		event.setParticipationType(request.getParticipationType());

		if (principal != null && principal.getOwnerType() == OwnerType.EMPLOYEE) {
			event.setCreatedByEmployee(employeeService.getScopedEntity(principal.getOwnerId()));
		}

		boolean opensIntoParticipation = request.getParticipationType() != null
				&& request.getParticipationType() != EventParticipationType.NONE;
		if (opensIntoParticipation) {
			if (principal == null) {
				throw new AccessDeniedException("Authentication is required to create a participation event");
			}
			if (principal.getRole() != Role.ADMIN && principal.getRole() != Role.TEACHER) {
				throw new AccessDeniedException("Only a teacher or admin can create a participation event");
			}
			if (request.getScope() == null) {
				throw new IllegalArgumentException("scope is required when participationType is set");
			}
			if (request.getStartAt() == null || request.getEndAt() == null) {
				throw new IllegalArgumentException("startAt/endAt are required when participationType is set");
			}
			if (request.getEndAt().isBefore(request.getStartAt())) {
				throw new IllegalArgumentException("endAt must not be before startAt");
			}
			if (request.getParticipationType() == EventParticipationType.REGISTRATION
					&& (request.getRegistrationFields() == null || request.getRegistrationFields().isEmpty())) {
				throw new IllegalArgumentException("registrationFields is required when participationType is REGISTRATION");
			}
			applyScope(principal, event, request.getScope(), request.getSectionId(), request.getClassName());
			if (request.getParticipationType() == EventParticipationType.REGISTRATION) {
				event.setRegistrationFieldsJson(writeJson(request.getRegistrationFields()));
			}
		}

		SchoolEvent saved = eventRepository.save(event);
		if (opensIntoParticipation) {
			notifyViaAnnouncement(principal, saved);
		}
		return toResponse(saved);
	}

	@Transactional
	public EventResponse update(AuthPrincipal principal, UUID id, EventRequest request) {
		SchoolEvent event = findScoped(id);
		if (isParticipationEvent(event)) {
			if (principal == null) {
				throw new AccessDeniedException("Authentication is required to edit a participation event");
			}
			requireEditor(principal, event);
		}
		applyRequest(event, request);
		if (request.getStatus() != null) {
			event.setStatus(request.getStatus());
		}
		if (request.getInflowEnabled() != null) {
			event.setInflowEnabled(request.getInflowEnabled());
		}
		if (request.getOutflowEnabled() != null) {
			event.setOutflowEnabled(request.getOutflowEnabled());
		}
		if (request.getVenue() != null) {
			event.setVenue(request.getVenue());
		}
		if (request.getStartAt() != null) {
			event.setStartAt(request.getStartAt());
		}
		if (request.getEndAt() != null) {
			event.setEndAt(request.getEndAt());
		}
		return toResponse(eventRepository.save(event));
	}

	/** Soft cancel for the participation flow - RSVPs/registrations/poll data stay intact for history. */
	@Transactional
	public void cancel(AuthPrincipal principal, UUID id) {
		SchoolEvent event = findScoped(id);
		requireEditor(principal, event);
		event.setCancelled(true);
		eventRepository.save(event);
	}

	public SchoolEvent getScopedEntity(UUID id) {
		return findScoped(id);
	}

	@Transactional
	public void submitRsvp(AuthPrincipal principal, UUID eventId, RsvpRequest request) {
		SchoolEvent event = requireParticipable(principal, eventId, EventParticipationType.RSVP);
		EventRsvp rsvp = eventRsvpRepository
				.findByEventIdAndOwnerIdAndOwnerType(eventId, principal.getOwnerId(), principal.getOwnerType())
				.orElseGet(() -> newRsvp(principal, event.getId()));
		rsvp.setStatus(request.getStatus());
		eventRsvpRepository.save(rsvp);
	}

	@Transactional(readOnly = true)
	public List<RsvpRosterEntry> listRsvps(AuthPrincipal principal, UUID eventId) {
		requireEditor(principal, findScoped(eventId));
		return eventRsvpRepository.findAllByEventId(eventId).stream()
				.map(r -> new RsvpRosterEntry(r.getOwnerId(), r.getOwnerType(),
						resolveName(r.getOwnerId(), r.getOwnerType(), principal.getSchoolId()), r.getStatus()))
				.toList();
	}

	@Transactional
	public void submitRegistration(AuthPrincipal principal, UUID eventId, SubmitRegistrationRequest request) {
		SchoolEvent event = requireParticipable(principal, eventId, EventParticipationType.REGISTRATION);
		validateAnswers(event, request.getAnswers());
		EventRegistration registration = eventRegistrationRepository
				.findByEventIdAndOwnerIdAndOwnerType(eventId, principal.getOwnerId(), principal.getOwnerType())
				.orElseGet(() -> newRegistration(principal, event.getId()));
		registration.setAnswers(writeJson(request.getAnswers()));
		eventRegistrationRepository.save(registration);
	}

	@Transactional(readOnly = true)
	public List<RegistrationEntry> listRegistrations(AuthPrincipal principal, UUID eventId) {
		requireEditor(principal, findScoped(eventId));
		return eventRegistrationRepository.findAllByEventId(eventId).stream()
				.map(r -> new RegistrationEntry(r.getId(), r.getOwnerId(), r.getOwnerType(),
						resolveName(r.getOwnerId(), r.getOwnerType(), principal.getSchoolId()),
						readJsonMap(r.getAnswers()), r.getCreatedAt()))
				.toList();
	}

	@Transactional
	public void createPollOptions(AuthPrincipal principal, UUID eventId, CreatePollOptionsRequest request) {
		SchoolEvent event = findScoped(eventId);
		requireEditor(principal, event);
		if (event.getParticipationType() != EventParticipationType.POLL) {
			throw new IllegalStateException("This event does not use a poll");
		}
		for (String label : request.getOptions()) {
			EventPollOption option = new EventPollOption();
			option.setSchoolId(principal.getSchoolId());
			option.setEventId(eventId);
			option.setLabel(label);
			eventPollOptionRepository.save(option);
		}
	}

	@Transactional(readOnly = true)
	public PollResponse getPoll(AuthPrincipal principal, UUID eventId) {
		SchoolEvent event = requireVisible(principal, eventId);
		if (event.getParticipationType() != EventParticipationType.POLL) {
			throw new IllegalStateException("This event does not use a poll");
		}
		List<EventPollOption> options = eventPollOptionRepository.findAllByEventId(eventId);
		Map<UUID, Long> counts = eventPollVoteRepository.findAllByEventId(eventId).stream()
				.collect(Collectors.groupingBy(EventPollVote::getOptionId, Collectors.counting()));
		List<PollOptionResult> results = options.stream()
				.map(o -> new PollOptionResult(o.getId(), o.getLabel(), counts.getOrDefault(o.getId(), 0L)))
				.toList();
		UUID myVote = eventPollVoteRepository
				.findByEventIdAndOwnerIdAndOwnerType(eventId, principal.getOwnerId(), principal.getOwnerType())
				.map(EventPollVote::getOptionId).orElse(null);
		return new PollResponse(results, myVote);
	}

	@Transactional
	public void vote(AuthPrincipal principal, UUID eventId, VoteRequest request) {
		SchoolEvent event = requireParticipable(principal, eventId, EventParticipationType.POLL);
		eventPollOptionRepository.findByIdAndEventId(request.getOptionId(), eventId)
				.orElseThrow(() -> new EntityNotFoundException("Poll option not found"));
		EventPollVote vote = eventPollVoteRepository
				.findByEventIdAndOwnerIdAndOwnerType(eventId, principal.getOwnerId(), principal.getOwnerType())
				.orElseGet(() -> newVote(principal, event.getId()));
		vote.setOptionId(request.getOptionId());
		eventPollVoteRepository.save(vote);
	}

	private EventRsvp newRsvp(AuthPrincipal principal, UUID eventId) {
		EventRsvp rsvp = new EventRsvp();
		rsvp.setSchoolId(principal.getSchoolId());
		rsvp.setEventId(eventId);
		rsvp.setOwnerId(principal.getOwnerId());
		rsvp.setOwnerType(principal.getOwnerType());
		return rsvp;
	}

	private EventRegistration newRegistration(AuthPrincipal principal, UUID eventId) {
		EventRegistration registration = new EventRegistration();
		registration.setSchoolId(principal.getSchoolId());
		registration.setEventId(eventId);
		registration.setOwnerId(principal.getOwnerId());
		registration.setOwnerType(principal.getOwnerType());
		return registration;
	}

	private EventPollVote newVote(AuthPrincipal principal, UUID eventId) {
		EventPollVote vote = new EventPollVote();
		vote.setSchoolId(principal.getSchoolId());
		vote.setEventId(eventId);
		vote.setOwnerId(principal.getOwnerId());
		vote.setOwnerType(principal.getOwnerType());
		return vote;
	}

	private boolean isParticipationEvent(SchoolEvent event) {
		return event.getParticipationType() != null && event.getParticipationType() != EventParticipationType.NONE;
	}

	/** Loaded, visible, and matches the event's own participation model without being cancelled. */
	private SchoolEvent requireParticipable(AuthPrincipal principal, UUID eventId, EventParticipationType expected) {
		SchoolEvent event = requireVisible(principal, eventId);
		if (event.getParticipationType() != expected) {
			throw new IllegalStateException("This event does not use " + expected.name().toLowerCase());
		}
		if (event.isCancelled()) {
			throw new IllegalStateException("This event has been cancelled");
		}
		return event;
	}

	private void validateAnswers(SchoolEvent event, Map<String, String> answers) {
		for (RegistrationFieldDefinition field : parseRegistrationFields(event)) {
			String value = answers.get(field.getKey());
			if (field.isRequired() && (value == null || value.isBlank())) {
				throw new IllegalArgumentException("Field '" + field.getLabel() + "' is required");
			}
		}
	}

	private void requireEditor(AuthPrincipal principal, SchoolEvent event) {
		boolean isCreator = principal.getOwnerType() == OwnerType.EMPLOYEE
				&& event.getCreatedByEmployee() != null
				&& event.getCreatedByEmployee().getId().equals(principal.getOwnerId());
		if (principal.getRole() != Role.ADMIN && !isCreator) {
			throw new AccessDeniedException("Only the creator or an admin can modify this event");
		}
	}

	private SchoolEvent requireVisible(AuthPrincipal principal, UUID eventId) {
		UUID schoolId = principal != null ? principal.getSchoolId() : schoolContext.getSchoolId();
		SchoolEvent event = findScoped(eventId, schoolId);
		if (!isVisibleTo(principal, event)) {
			throw new AccessDeniedException("You do not have visibility into this event");
		}
		return event;
	}

	/**
	 * Legacy finance-only events (scope == null) stay visible to everyone, exactly as before this
	 * feature existed. Only events that opted into a scope get the class/grade restriction - which
	 * requires a principal, so an unauthenticated caller simply can't reach a scoped event by id.
	 */
	private boolean isVisibleTo(AuthPrincipal principal, SchoolEvent event) {
		if (event.getScope() == null || event.getScope() == EventScope.SCHOOL) {
			return true;
		}
		if (principal == null) {
			return false;
		}
		if (event.getScope() == EventScope.CLASS) {
			return announcementService.isSectionVisibleTo(principal, event.getSectionId());
		}
		return announcementService.isGradeVisibleTo(principal, event.getClassName());
	}

	/** Mirrors AnnouncementService.create()'s scope authorization exactly, so the two features never disagree. */
	private void applyScope(AuthPrincipal principal, SchoolEvent event, EventScope scope, UUID sectionId, String className) {
		UUID schoolId = principal.getSchoolId();
		event.setScope(scope);
		if (scope == EventScope.SCHOOL) {
			if (principal.getRole() != Role.ADMIN) {
				throw new AccessDeniedException("Only an admin can create a school-wide event");
			}
		} else if (scope == EventScope.CLASS) {
			if (sectionId == null) {
				throw new IllegalArgumentException("sectionId is required for a class-level event");
			}
			ClassSection section = classSectionRepository.findByIdAndSchoolId(sectionId, schoolId)
					.orElseThrow(() -> new EntityNotFoundException("Class-section not found"));
			boolean isClassTeacher = section.getClassTeacher() != null
					&& section.getClassTeacher().getId().equals(principal.getOwnerId());
			if (principal.getRole() != Role.ADMIN && !(principal.getRole() == Role.TEACHER && isClassTeacher)) {
				throw new AccessDeniedException("Only an admin or this section's class teacher can create a class-level event");
			}
			event.setSectionId(section.getId());
		} else {
			if (className == null || className.isBlank()) {
				throw new IllegalArgumentException("className is required for a grade-level event");
			}
			List<ClassSection> sections =
					classSectionRepository.findAllBySchoolIdAndClassNameOrderBySectionAsc(schoolId, className);
			if (sections.isEmpty()) {
				throw new EntityNotFoundException("No sections found for that class");
			}
			boolean isClassTeacherOfGrade = sections.stream().anyMatch(s ->
					s.getClassTeacher() != null && s.getClassTeacher().getId().equals(principal.getOwnerId()));
			if (principal.getRole() != Role.ADMIN && !(principal.getRole() == Role.TEACHER && isClassTeacherOfGrade)) {
				throw new AccessDeniedException("Only an admin or one of this grade's class teachers can create a grade-level event");
			}
			event.setClassName(className);
		}
	}

	private void notifyViaAnnouncement(AuthPrincipal principal, SchoolEvent event) {
		CreateAnnouncementRequest request = new CreateAnnouncementRequest();
		request.setScope(AnnouncementScope.valueOf(event.getScope().name()));
		request.setSectionId(event.getSectionId());
		request.setClassName(event.getClassName());
		request.setTitle("New event: " + event.getName());
		request.setBody(buildAnnouncementBody(event));
		announcementService.create(principal, request);
	}

	private String buildAnnouncementBody(SchoolEvent event) {
		StringBuilder body = new StringBuilder();
		if (event.getDescription() != null && !event.getDescription().isBlank()) {
			body.append(event.getDescription()).append(" ");
		}
		if (event.getVenue() != null && !event.getVenue().isBlank()) {
			body.append("Venue: ").append(event.getVenue()).append(". ");
		}
		body.append(event.getStartAt()).append(" to ").append(event.getEndAt()).append(".");
		return body.toString();
	}

	private String resolveName(UUID ownerId, OwnerType ownerType, UUID schoolId) {
		if (ownerType == OwnerType.STUDENT) {
			return studentRepository.findByIdAndSchoolId(ownerId, schoolId).map(Student::getName).orElse("Unknown");
		}
		return employeeRepository.findByIdAndSchoolId(ownerId, schoolId).map(Employee::getName).orElse("Unknown");
	}

	private SchoolEvent findScoped(UUID id) {
		return findScoped(id, schoolContext.getSchoolId());
	}

	private SchoolEvent findScoped(UUID id, UUID schoolId) {
		return eventRepository.findByIdAndSchoolId(id, schoolId)
				.orElseThrow(() -> new EntityNotFoundException("Event not found"));
	}

	private void applyRequest(SchoolEvent event, EventRequest request) {
		event.setName(request.getName());
		event.setDescription(request.getDescription());
		event.setEventDate(request.getEventDate());
	}

	private EventResponse toResponse(SchoolEvent event) {
		List<RegistrationFieldDefinition> fields = event.getParticipationType() == EventParticipationType.REGISTRATION
				? parseRegistrationFields(event) : null;
		return EventResponse.from(event, computeParticipationStatus(event), fields);
	}

	private EventParticipationStatus computeParticipationStatus(SchoolEvent event) {
		if (event.getParticipationType() == null || event.getParticipationType() == EventParticipationType.NONE) {
			return null;
		}
		if (event.isCancelled()) {
			return EventParticipationStatus.CANCELLED;
		}
		Instant now = Instant.now();
		if (now.isBefore(event.getStartAt())) {
			return EventParticipationStatus.UPCOMING;
		}
		if (now.isAfter(event.getEndAt())) {
			return EventParticipationStatus.COMPLETED;
		}
		return EventParticipationStatus.ONGOING;
	}

	private List<RegistrationFieldDefinition> parseRegistrationFields(SchoolEvent event) {
		if (event.getRegistrationFieldsJson() == null) {
			return List.of();
		}
		try {
			return objectMapper.readValue(event.getRegistrationFieldsJson(), new TypeReference<List<RegistrationFieldDefinition>>() { });
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Corrupt registrationFields JSON for event " + event.getId(), e);
		}
	}

	private Map<String, String> readJsonMap(String json) {
		try {
			return objectMapper.readValue(json, new TypeReference<Map<String, String>>() { });
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Corrupt answers JSON", e);
		}
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize JSON", e);
		}
	}

}
