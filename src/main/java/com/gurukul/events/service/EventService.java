package com.gurukul.events.service;

import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.events.dto.EventRequest;
import com.gurukul.events.dto.EventResponse;
import com.gurukul.events.entity.EventStatus;
import com.gurukul.events.entity.SchoolEvent;
import com.gurukul.events.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventService {

	private final EventRepository eventRepository;
	private final SchoolContext schoolContext;

	public List<EventResponse> list() {
		return eventRepository.findAllBySchoolIdOrderByEventDateDesc(schoolContext.getSchoolId()).stream()
				.map(EventResponse::from)
				.toList();
	}

	public EventResponse getById(UUID id) {
		return EventResponse.from(findScoped(id));
	}

	@Transactional
	public EventResponse create(EventRequest request) {
		SchoolEvent event = new SchoolEvent();
		event.setSchoolId(schoolContext.getSchoolId());
		applyRequest(event, request);
		event.setStatus(request.getStatus() != null ? request.getStatus() : EventStatus.DRAFT);
		event.setInflowEnabled(Boolean.TRUE.equals(request.getInflowEnabled()));
		event.setOutflowEnabled(Boolean.TRUE.equals(request.getOutflowEnabled()));
		return EventResponse.from(eventRepository.save(event));
	}

	@Transactional
	public EventResponse update(UUID id, EventRequest request) {
		SchoolEvent event = findScoped(id);
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
		return EventResponse.from(eventRepository.save(event));
	}

	public SchoolEvent getScopedEntity(UUID id) {
		return findScoped(id);
	}

	private SchoolEvent findScoped(UUID id) {
		return eventRepository.findByIdAndSchoolId(id, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Event not found"));
	}

	private void applyRequest(SchoolEvent event, EventRequest request) {
		event.setName(request.getName());
		event.setDescription(request.getDescription());
		event.setEventDate(request.getEventDate());
	}

}
