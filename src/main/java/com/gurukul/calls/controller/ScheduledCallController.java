package com.gurukul.calls.controller;

import com.gurukul.auth.security.AuthContext;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.calls.dto.CallDtos.MyInviteResponse;
import com.gurukul.calls.dto.CallDtos.RsvpRequest;
import com.gurukul.calls.dto.CallDtos.ScheduleCallRequest;
import com.gurukul.calls.dto.CallDtos.ScheduledCallResponse;
import com.gurukul.calls.entity.ScheduledCall;
import com.gurukul.calls.service.ScheduledCallService;
import com.gurukul.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Video Calls - Scheduled", description = "Schedule a video call with one or more invitees and manage RSVPs. "
		+ "Requires X-School-Id and Authorization headers. Live signaling (incoming/started/reminder events) "
		+ "arrives over WebSocket/STOMP on the caller's personal topic - see the Calls WebSocket docs.")
public class ScheduledCallController {

	private final ScheduledCallService scheduledCallService;

	public ScheduledCallController(ScheduledCallService scheduledCallService) {
		this.scheduledCallService = scheduledCallService;
	}

	@PostMapping("/api/v1/calls/scheduled")
	@Operation(summary = "Schedule a call with one or more invitees")
	public ApiResponse<ScheduledCallResponse> create(@Valid @RequestBody ScheduleCallRequest request) {
		ScheduledCall call = scheduledCallService.create(AuthContext.current(), request);
		return ApiResponse.success(toResponse(call));
	}

	@GetMapping("/api/v1/calls/scheduled/hosted")
	@Operation(summary = "List calls I'm hosting, most recently scheduled first")
	public ApiResponse<List<ScheduledCallResponse>> hosted() {
		List<ScheduledCallResponse> responses = scheduledCallService.hostedBy(AuthContext.current()).stream()
				.map(this::toResponse)
				.toList();
		return ApiResponse.success(responses);
	}

	@GetMapping("/api/v1/calls/scheduled/invited")
	@Operation(summary = "List calls I've been invited to, with my RSVP status")
	public ApiResponse<List<MyInviteResponse>> invited() {
		return ApiResponse.success(scheduledCallService.invitedTo(AuthContext.current()));
	}

	@GetMapping("/api/v1/calls/scheduled/{id}")
	@Operation(summary = "Get a scheduled call (host or an invitee only)")
	public ApiResponse<ScheduledCallResponse> get(@PathVariable UUID id) {
		ScheduledCall call = scheduledCallService.requireVisible(AuthContext.current(), id);
		return ApiResponse.success(toResponse(call));
	}

	@PostMapping("/api/v1/calls/scheduled/{id}/rsvp")
	@Operation(summary = "Respond to an invite (Accept/Decline)")
	public ApiResponse<ScheduledCallResponse> rsvp(@PathVariable UUID id, @Valid @RequestBody RsvpRequest request) {
		AuthPrincipal principal = AuthContext.current();
		scheduledCallService.rsvp(principal, id, request.getStatus());
		return ApiResponse.success(toResponse(scheduledCallService.requireScheduled(principal.getSchoolId(), id)));
	}

	@PostMapping("/api/v1/calls/scheduled/{id}/cancel")
	@Operation(summary = "Cancel a scheduled call (host only)")
	public ApiResponse<ScheduledCallResponse> cancel(@PathVariable UUID id) {
		ScheduledCall call = scheduledCallService.cancel(AuthContext.current(), id);
		return ApiResponse.success(toResponse(call));
	}

	@PostMapping("/api/v1/calls/scheduled/{id}/start")
	@Operation(summary = "Start a scheduled call now (host only) - notifies every ACCEPTED invitee to join")
	public ApiResponse<ScheduledCallResponse> start(@PathVariable UUID id) {
		ScheduledCall call = scheduledCallService.start(AuthContext.current(), id);
		return ApiResponse.success(toResponse(call));
	}

	@PostMapping("/api/v1/calls/scheduled/{id}/end")
	@Operation(summary = "Mark a started call's session as finished (host only)")
	public ApiResponse<ScheduledCallResponse> end(@PathVariable UUID id) {
		ScheduledCall call = scheduledCallService.endSession(AuthContext.current(), id);
		return ApiResponse.success(toResponse(call));
	}

	private ScheduledCallResponse toResponse(ScheduledCall call) {
		return ScheduledCallResponse.from(call, scheduledCallService.invitees(call.getId()));
	}

}
