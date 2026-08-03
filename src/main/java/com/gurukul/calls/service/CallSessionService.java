package com.gurukul.calls.service;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.calls.dto.CallEvent;
import com.gurukul.calls.entity.CallLog;
import com.gurukul.calls.entity.CallOutcome;
import com.gurukul.calls.jitsi.JitsiBotService;
import com.gurukul.calls.repository.CallLogRepository;
import com.gurukul.common.EntityNotFoundException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immediate (ad-hoc) 1:1 calls. Jitsi's own server handles the actual WebRTC signaling/media once
 * both sides join callLog.roomName - this service only decides who may call whom, tracks
 * ringing/busy state, and notifies the other party over the personal call topic (see
 * CallEventPublisher). There is no offer/answer/ICE relay here by design.
 */
@Service
public class CallSessionService {

	private static final Duration RING_TIMEOUT = Duration.ofSeconds(45);

	private final CallLogRepository callLogRepository;
	private final CallAuthorizationService callAuthorizationService;
	private final ActiveCallRegistry activeCallRegistry;
	private final CallEventPublisher eventPublisher;
	private final TaskScheduler taskScheduler;
	private final JitsiBotService jitsiBotService;

	public CallSessionService(
			CallLogRepository callLogRepository,
			CallAuthorizationService callAuthorizationService,
			ActiveCallRegistry activeCallRegistry,
			CallEventPublisher eventPublisher,
			TaskScheduler taskScheduler,
			JitsiBotService jitsiBotService) {
		this.callLogRepository = callLogRepository;
		this.callAuthorizationService = callAuthorizationService;
		this.activeCallRegistry = activeCallRegistry;
		this.eventPublisher = eventPublisher;
		this.taskScheduler = taskScheduler;
		this.jitsiBotService = jitsiBotService;
	}

	@Transactional
	public CallLog startImmediateCall(AuthPrincipal principal, OwnerType calleeType, UUID calleeId) {
		UUID schoolId = principal.getSchoolId();
		OwnerType callerType = principal.getOwnerType();
		UUID callerId = principal.getOwnerId();

		callAuthorizationService.requireExists(schoolId, calleeType, calleeId);
		callAuthorizationService.requireCanCall(principal, calleeType, calleeId);

		if (activeCallRegistry.isBusy(callerType, callerId)) {
			throw new IllegalStateException("You are already on a call");
		}
		if (activeCallRegistry.isBusy(calleeType, calleeId)) {
			return callLogRepository.save(newLog(schoolId, callerType, callerId, calleeType, calleeId, CallOutcome.BUSY, true));
		}

		CallLog callLog = callLogRepository.save(
				newLog(schoolId, callerType, callerId, calleeType, calleeId, CallOutcome.IN_PROGRESS, false));

		activeCallRegistry.markActive(callerType, callerId, callLog.getId());
		activeCallRegistry.markActive(calleeType, calleeId, callLog.getId());

		// Must happen before the callee is notified below - otherwise a fast callee could join the
		// Jitsi room before the bot has finished "starting" it. Best-effort/no-op when unconfigured
		// (see JitsiBotService); holds this transaction's DB connection open a little longer when
		// it does run, an accepted tradeoff at this app's current single-instance scale.
		jitsiBotService.warmRoom(callLog.getRoomName());

		eventPublisher.sendTo(schoolId, calleeType, calleeId, CallEvent.incomingCall(callLog, callerType, callerId));
		taskScheduler.schedule(() -> handleRingTimeout(callLog.getId()), Instant.now().plus(RING_TIMEOUT));

		return callLog;
	}

	@Transactional
	public CallLog respond(AuthPrincipal principal, UUID callLogId, boolean accept) {
		CallLog callLog = requireRinging(principal, callLogId, /* asCallee= */ true);

		if (accept) {
			callLog.setOutcome(CallOutcome.IN_PROGRESS);
			callLogRepository.save(callLog);
			notifyOther(callLog, principal.getOwnerType(), principal.getOwnerId(), CallEvent.Type.CALL_ACCEPTED);
		} else {
			finish(callLog, CallOutcome.DECLINED);
			notifyOther(callLog, principal.getOwnerType(), principal.getOwnerId(), CallEvent.Type.CALL_DECLINED);
		}
		return callLog;
	}

	@Transactional
	public CallLog cancel(AuthPrincipal principal, UUID callLogId) {
		CallLog callLog = requireRinging(principal, callLogId, /* asCallee= */ false);
		finish(callLog, CallOutcome.CANCELLED);
		notifyOther(callLog, principal.getOwnerType(), principal.getOwnerId(), CallEvent.Type.CALL_CANCELLED);
		return callLog;
	}

	@Transactional
	public CallLog end(AuthPrincipal principal, UUID callLogId) {
		CallLog callLog = callLogRepository.findByIdAndSchoolId(callLogId, principal.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Call not found"));
		requireParticipant(callLog, principal);
		if (callLog.getEndedAt() == null) {
			finish(callLog, CallOutcome.COMPLETED);
			notifyOther(callLog, principal.getOwnerType(), principal.getOwnerId(), CallEvent.Type.CALL_ENDED);
		}
		return callLog;
	}

	public List<CallLog> history(AuthPrincipal principal) {
		return callLogRepository.findAllForParticipant(
				principal.getSchoolId(), principal.getOwnerType(), principal.getOwnerId());
	}

	private void handleRingTimeout(UUID callLogId) {
		CallLog callLog = callLogRepository.findById(callLogId).orElse(null);
		if (callLog == null || callLog.getOutcome() != CallOutcome.IN_PROGRESS || callLog.getEndedAt() != null) {
			return;
		}
		finish(callLog, CallOutcome.MISSED);
		notifyOther(callLog, callLog.getCalleeOwnerType(), callLog.getCalleeOwnerId(), CallEvent.Type.CALL_MISSED);
	}

	private CallLog requireRinging(AuthPrincipal principal, UUID callLogId, boolean asCallee) {
		CallLog callLog = callLogRepository.findByIdAndSchoolId(callLogId, principal.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Call not found"));
		OwnerType expectedType = asCallee ? callLog.getCalleeOwnerType() : callLog.getCallerOwnerType();
		UUID expectedId = asCallee ? callLog.getCalleeOwnerId() : callLog.getCallerOwnerId();
		if (expectedType != principal.getOwnerType() || !expectedId.equals(principal.getOwnerId())) {
			throw new AccessDeniedException("You are not " + (asCallee ? "the callee" : "the caller") + " on this call");
		}
		if (callLog.getOutcome() != CallOutcome.IN_PROGRESS || callLog.getEndedAt() != null) {
			throw new IllegalStateException("This call is no longer ringing");
		}
		return callLog;
	}

	private void requireParticipant(CallLog callLog, AuthPrincipal principal) {
		boolean isCaller = callLog.getCallerOwnerType() == principal.getOwnerType()
				&& callLog.getCallerOwnerId().equals(principal.getOwnerId());
		boolean isCallee = callLog.getCalleeOwnerType() == principal.getOwnerType()
				&& principal.getOwnerId().equals(callLog.getCalleeOwnerId());
		if (!isCaller && !isCallee) {
			throw new AccessDeniedException("You are not a participant on this call");
		}
	}

	private void finish(CallLog callLog, CallOutcome outcome) {
		callLog.setOutcome(outcome);
		callLog.setEndedAt(Instant.now());
		callLog.setDurationSeconds(
				outcome == CallOutcome.COMPLETED
						? Duration.between(callLog.getStartedAt(), callLog.getEndedAt()).getSeconds()
						: 0L);
		callLogRepository.save(callLog);
		activeCallRegistry.clear(callLog.getCallerOwnerType(), callLog.getCallerOwnerId());
		if (callLog.getCalleeOwnerType() != null) {
			activeCallRegistry.clear(callLog.getCalleeOwnerType(), callLog.getCalleeOwnerId());
		}
	}

	private void notifyOther(CallLog callLog, OwnerType actorType, UUID actorId, CallEvent.Type type) {
		boolean actorIsCaller = callLog.getCallerOwnerType() == actorType && callLog.getCallerOwnerId().equals(actorId);
		OwnerType targetType = actorIsCaller ? callLog.getCalleeOwnerType() : callLog.getCallerOwnerType();
		UUID targetId = actorIsCaller ? callLog.getCalleeOwnerId() : callLog.getCallerOwnerId();
		if (targetType == null) {
			return;
		}
		eventPublisher.sendTo(callLog.getSchoolId(), targetType, targetId, CallEvent.simple(type, callLog));
	}

	private CallLog newLog(
			UUID schoolId, OwnerType callerType, UUID callerId, OwnerType calleeType, UUID calleeId,
			CallOutcome outcome, boolean endedImmediately) {
		CallLog callLog = new CallLog();
		callLog.setSchoolId(schoolId);
		callLog.setCallerOwnerType(callerType);
		callLog.setCallerOwnerId(callerId);
		callLog.setCalleeOwnerType(calleeType);
		callLog.setCalleeOwnerId(calleeId);
		callLog.setRoomName(RoomNames.generate());
		callLog.setStartedAt(Instant.now());
		callLog.setOutcome(outcome);
		if (endedImmediately) {
			callLog.setEndedAt(callLog.getStartedAt());
			callLog.setDurationSeconds(0L);
		}
		return callLog;
	}

}
