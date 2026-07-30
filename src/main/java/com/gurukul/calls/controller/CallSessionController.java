package com.gurukul.calls.controller;

import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthContext;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.calls.dto.CallDtos.CallLogResponse;
import com.gurukul.calls.dto.CallDtos.CallSessionResponse;
import com.gurukul.calls.dto.CallDtos.StartImmediateCallRequest;
import com.gurukul.calls.entity.CallLog;
import com.gurukul.calls.repository.CallLogRepository;
import com.gurukul.calls.service.CallSessionService;
import com.gurukul.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Video Calls - Immediate", description = "Ad-hoc 1:1 calls, started right away rather than scheduled. "
		+ "The callee is notified over WebSocket/STOMP on their personal call topic and responds via /accept or "
		+ "/decline; a call left unanswered for 45s is auto-marked MISSED. Jitsi's own server handles the actual "
		+ "media/signaling once both sides join callSessionResponse.roomName - this API only decides who may call "
		+ "whom and tracks ringing/busy state.")
public class CallSessionController {

	private final CallSessionService callSessionService;
	private final CallLogRepository callLogRepository;

	public CallSessionController(CallSessionService callSessionService, CallLogRepository callLogRepository) {
		this.callSessionService = callSessionService;
		this.callLogRepository = callLogRepository;
	}

	@PostMapping("/api/v1/calls/immediate")
	@Operation(summary = "Start an immediate call")
	public ApiResponse<CallSessionResponse> start(@Valid @RequestBody StartImmediateCallRequest request) {
		CallLog callLog = callSessionService.startImmediateCall(
				AuthContext.current(), request.getCalleeOwnerType(), request.getCalleeOwnerId());
		return ApiResponse.success(CallSessionResponse.from(callLog));
	}

	@PostMapping("/api/v1/calls/{id}/accept")
	@Operation(summary = "Accept an incoming call (callee only)")
	public ApiResponse<CallSessionResponse> accept(@PathVariable UUID id) {
		CallLog callLog = callSessionService.respond(AuthContext.current(), id, true);
		return ApiResponse.success(CallSessionResponse.from(callLog));
	}

	@PostMapping("/api/v1/calls/{id}/decline")
	@Operation(summary = "Decline an incoming call (callee only)")
	public ApiResponse<CallSessionResponse> decline(@PathVariable UUID id) {
		CallLog callLog = callSessionService.respond(AuthContext.current(), id, false);
		return ApiResponse.success(CallSessionResponse.from(callLog));
	}

	@PostMapping("/api/v1/calls/{id}/cancel")
	@Operation(summary = "Cancel a call while it's still ringing (caller only)")
	public ApiResponse<CallSessionResponse> cancel(@PathVariable UUID id) {
		CallLog callLog = callSessionService.cancel(AuthContext.current(), id);
		return ApiResponse.success(CallSessionResponse.from(callLog));
	}

	@PostMapping("/api/v1/calls/{id}/end")
	@Operation(summary = "End an in-progress call (either participant)")
	public ApiResponse<CallSessionResponse> end(@PathVariable UUID id) {
		CallLog callLog = callSessionService.end(AuthContext.current(), id);
		return ApiResponse.success(CallSessionResponse.from(callLog));
	}

	@GetMapping("/api/v1/calls/history")
	@Operation(summary = "My call history (immediate and scheduled sessions I took part in)")
	public ApiResponse<List<CallLogResponse>> history() {
		List<CallLogResponse> responses = callSessionService.history(AuthContext.current()).stream()
				.map(CallLogResponse::from)
				.toList();
		return ApiResponse.success(responses);
	}

	@GetMapping("/api/v1/calls/history/school")
	@Operation(summary = "Every call in the school (admin only)")
	public ApiResponse<List<CallLogResponse>> schoolHistory() {
		AuthPrincipal principal = AuthContext.current();
		if (principal.getRole() != Role.ADMIN) {
			throw new AccessDeniedException("Only an admin can view the whole school's call history");
		}
		List<CallLogResponse> responses = callLogRepository.findAllBySchoolIdOrderByStartedAtDesc(principal.getSchoolId())
				.stream()
				.map(CallLogResponse::from)
				.toList();
		return ApiResponse.success(responses);
	}

}
