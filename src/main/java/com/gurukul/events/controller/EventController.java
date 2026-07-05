package com.gurukul.events.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.events.dto.EventRequest;
import com.gurukul.events.dto.EventResponse;
import com.gurukul.events.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Events", description = "School events for collections and expenses. Requires X-School-Id header.")
public class EventController {

	private final EventService eventService;

	@GetMapping
	@Operation(summary = "List events")
	public ApiResponse<List<EventResponse>> list() {
		return ApiResponse.success(eventService.list());
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get event by ID")
	public ApiResponse<EventResponse> getById(@PathVariable UUID id) {
		return ApiResponse.success(eventService.getById(id));
	}

	@PostMapping
	@Operation(summary = "Create event")
	public ApiResponse<EventResponse> create(@Valid @RequestBody EventRequest request) {
		return ApiResponse.success(eventService.create(request), "Event created");
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update event")
	public ApiResponse<EventResponse> update(@PathVariable UUID id, @Valid @RequestBody EventRequest request) {
		return ApiResponse.success(eventService.update(id, request), "Event updated");
	}

}
