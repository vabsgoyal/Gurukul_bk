package com.gurukul.events.controller;

import com.gurukul.auth.security.AuthContext;
import com.gurukul.common.ApiResponse;
import com.gurukul.events.dto.EventDtos.CreatePollOptionsRequest;
import com.gurukul.events.dto.EventDtos.PollResponse;
import com.gurukul.events.dto.EventDtos.RegistrationEntry;
import com.gurukul.events.dto.EventDtos.RsvpRequest;
import com.gurukul.events.dto.EventDtos.RsvpRosterEntry;
import com.gurukul.events.dto.EventDtos.SubmitRegistrationRequest;
import com.gurukul.events.dto.EventDtos.VoteRequest;
import com.gurukul.events.dto.EventRequest;
import com.gurukul.events.dto.EventResponse;
import com.gurukul.events.entity.EventParticipationStatus;
import com.gurukul.events.entity.EventScope;
import com.gurukul.events.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Events", description = "School events. Originally finance-tracking only (collections/expenses); "
		+ "now also supports a participation flow (RSVP, a configurable registration form, or a poll - "
		+ "exactly one per event) with SCHOOL/CLASS/GRADE scoping and Announcement-based notification on "
		+ "creation. A plain finance event (no category/scope/participationType) behaves exactly as before. "
		+ "Requires X-School-Id header.")
public class EventController {

	private final EventService eventService;

	@GetMapping
	@Operation(summary = "List events visible to the caller, optionally filtered by scope/status. "
			+ "Callers with no JWT (legacy finance clients) see every event, unfiltered, as before.")
	public ApiResponse<List<EventResponse>> list(
			@RequestParam(required = false) EventScope scope,
			@RequestParam(required = false) EventParticipationStatus status) {
		return ApiResponse.success(eventService.listVisible(AuthContext.currentOrNull(), scope, status));
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get event by ID")
	public ApiResponse<EventResponse> getById(@PathVariable UUID id) {
		return ApiResponse.success(eventService.getById(AuthContext.currentOrNull(), id));
	}

	@PostMapping
	@Operation(summary = "Create event - plain finance event (no auth required, unchanged) or a "
			+ "participation event (set category/scope/startAt/endAt/participationType; requires "
			+ "authentication - teacher for their own class/grade, admin for any scope)")
	public ApiResponse<EventResponse> create(@Valid @RequestBody EventRequest request) {
		return ApiResponse.success(eventService.create(AuthContext.currentOrNull(), request), "Event created");
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update event (creator or admin only for a participation event; unrestricted for a plain finance event)")
	public ApiResponse<EventResponse> update(@PathVariable UUID id, @Valid @RequestBody EventRequest request) {
		return ApiResponse.success(eventService.update(AuthContext.currentOrNull(), id, request), "Event updated");
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Cancel a participation event (creator or admin only) - soft cancel, not a hard delete")
	public ApiResponse<Void> cancel(@PathVariable UUID id) {
		eventService.cancel(AuthContext.current(), id);
		return ApiResponse.success(null, "Event cancelled");
	}

	@PostMapping("/{id}/rsvp")
	@Operation(summary = "RSVP to an event (any role) - resubmitting updates your existing response")
	public ApiResponse<Void> rsvp(@PathVariable UUID id, @Valid @RequestBody RsvpRequest request) {
		eventService.submitRsvp(AuthContext.current(), id, request);
		return ApiResponse.success(null, "RSVP recorded");
	}

	@GetMapping("/{id}/rsvps")
	@Operation(summary = "RSVP roster (creator or admin only)")
	public ApiResponse<List<RsvpRosterEntry>> rsvps(@PathVariable UUID id) {
		return ApiResponse.success(eventService.listRsvps(AuthContext.current(), id));
	}

	@PostMapping("/{id}/registrations")
	@Operation(summary = "Submit a registration (any role) - resubmitting replaces your existing entry")
	public ApiResponse<Void> register(@PathVariable UUID id, @Valid @RequestBody SubmitRegistrationRequest request) {
		eventService.submitRegistration(AuthContext.current(), id, request);
		return ApiResponse.success(null, "Registration recorded");
	}

	@GetMapping("/{id}/registrations")
	@Operation(summary = "Registration roster (creator or admin only)")
	public ApiResponse<List<RegistrationEntry>> registrations(@PathVariable UUID id) {
		return ApiResponse.success(eventService.listRegistrations(AuthContext.current(), id));
	}

	@PostMapping("/{id}/poll/options")
	@Operation(summary = "Set up poll options (creator or admin only)")
	public ApiResponse<Void> createPollOptions(@PathVariable UUID id, @Valid @RequestBody CreatePollOptionsRequest request) {
		eventService.createPollOptions(AuthContext.current(), id, request);
		return ApiResponse.success(null, "Poll options added");
	}

	@GetMapping("/{id}/poll")
	@Operation(summary = "Poll options with live vote counts, plus the caller's own vote if any")
	public ApiResponse<PollResponse> poll(@PathVariable UUID id) {
		return ApiResponse.success(eventService.getPoll(AuthContext.current(), id));
	}

	@PostMapping("/{id}/poll/vote")
	@Operation(summary = "Vote in the poll (any role) - one vote per user, resubmitting changes it")
	public ApiResponse<Void> vote(@PathVariable UUID id, @Valid @RequestBody VoteRequest request) {
		eventService.vote(AuthContext.current(), id, request);
		return ApiResponse.success(null, "Vote recorded");
	}

}
