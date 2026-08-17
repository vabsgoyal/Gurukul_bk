package com.gurukul.calls.service;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.calls.dto.CallEvent;
import com.gurukul.calls.entity.CallLog;
import com.gurukul.calls.entity.CallOutcome;
import com.gurukul.calls.entity.CallProvider;
import com.gurukul.calls.repository.CallLogRepository;
import com.gurukul.common.EntityNotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
	private final CallProviderResolver callProviderResolver;
	private final TransactionTemplate transactionTemplate;

	public CallSessionService(
			CallLogRepository callLogRepository,
			CallAuthorizationService callAuthorizationService,
			ActiveCallRegistry activeCallRegistry,
			CallEventPublisher eventPublisher,
			TaskScheduler taskScheduler,
			CallProviderResolver callProviderResolver,
			PlatformTransactionManager transactionManager) {
		this.callLogRepository = callLogRepository;
		this.callAuthorizationService = callAuthorizationService;
		this.activeCallRegistry = activeCallRegistry;
		this.eventPublisher = eventPublisher;
		this.taskScheduler = taskScheduler;
		this.callProviderResolver = callProviderResolver;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

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
			return transactionTemplate.execute(status -> callLogRepository.save(
					newLog(schoolId, callerType, callerId, calleeType, calleeId, CallOutcome.BUSY, true,
							RoomNames.generate(), CallProvider.JITSI)));
		}

		// Resolved (Google Meet event created, or Jitsi room warmed) before any DB writes or the
		// callee notification - otherwise a fast callee could join before the room/meeting is ready.
		// Deliberately run outside a transaction: either path can take a few seconds, and must not
		// hold a DB connection open that long. The caller is treated as the "host" for Google Meet
		// purposes - see CallProviderResolver.
		CallProviderResolver.Resolution resolution = callProviderResolver.resolveForImmediateUse(
				callerType, callerId, "Gurukul call", Instant.now(), Instant.now().plus(Duration.ofHours(1)));

		CallLog callLog = transactionTemplate.execute(status -> {
			CallLog saved = callLogRepository.save(
					newLog(schoolId, callerType, callerId, calleeType, calleeId, CallOutcome.IN_PROGRESS, false,
							resolution.roomNameOrUrl(), resolution.provider()));
			activeCallRegistry.markActive(callerType, callerId, saved.getId());
			activeCallRegistry.markActive(calleeType, calleeId, saved.getId());
			return saved;
		});

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

	@Transactional(readOnly = true)
	public Slice<CallLog> history(AuthPrincipal principal, Pageable pageable) {
		return callLogRepository.findAllForParticipant(
				principal.getSchoolId(), principal.getOwnerType(), principal.getOwnerId(), pageable);
	}

	/** Only worth calling on page 0 - see CallSessionController. */
	@Transactional(readOnly = true)
	public long countHistory(AuthPrincipal principal) {
		return callLogRepository.countForParticipant(principal.getSchoolId(), principal.getOwnerType(), principal.getOwnerId());
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
			CallOutcome outcome, boolean endedImmediately, String roomName, CallProvider provider) {
		CallLog callLog = new CallLog();
		callLog.setSchoolId(schoolId);
		callLog.setCallerOwnerType(callerType);
		callLog.setCallerOwnerId(callerId);
		callLog.setCalleeOwnerType(calleeType);
		callLog.setCalleeOwnerId(calleeId);
		callLog.setRoomName(roomName);
		callLog.setProvider(provider);
		callLog.setStartedAt(Instant.now());
		callLog.setOutcome(outcome);
		if (endedImmediately) {
			callLog.setEndedAt(callLog.getStartedAt());
			callLog.setDurationSeconds(0L);
		}
		return callLog;
	}

}
