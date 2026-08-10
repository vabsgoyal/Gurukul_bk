package com.gurukul.calls.service;

import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.calls.dto.CallDtos.MyInviteResponse;
import com.gurukul.calls.dto.CallDtos.ScheduleCallRequest;
import com.gurukul.calls.dto.CallEvent;
import com.gurukul.calls.entity.CallInvitee;
import com.gurukul.calls.entity.CallLog;
import com.gurukul.calls.entity.CallOutcome;
import com.gurukul.calls.entity.CallStatus;
import com.gurukul.calls.entity.RsvpStatus;
import com.gurukul.calls.entity.ScheduledCall;
import com.gurukul.calls.jitsi.JitsiBotService;
import com.gurukul.calls.repository.CallInviteeRepository;
import com.gurukul.calls.repository.CallLogRepository;
import com.gurukul.calls.repository.ScheduledCallRepository;
import com.gurukul.common.EntityNotFoundException;
import org.springframework.scheduling.annotation.Scheduled;
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
 * Scheduling + RSVP for calls with one or more invitees. A scheduled call's actual session -
 * once started - is tracked the same way as an immediate call (a CallLog row), just linked back
 * via scheduledCallId instead of a single calleeOwnerType/Id.
 */
@Service
public class ScheduledCallService {

	private static final Duration REMINDER_LEAD = Duration.ofMinutes(30);
	private static final Duration EXPIRE_GRACE = Duration.ofMinutes(30);

	private final ScheduledCallRepository scheduledCallRepository;
	private final CallInviteeRepository callInviteeRepository;
	private final CallLogRepository callLogRepository;
	private final CallAuthorizationService callAuthorizationService;
	private final CallEventPublisher eventPublisher;
	private final JitsiBotService jitsiBotService;
	private final TransactionTemplate transactionTemplate;

	public ScheduledCallService(
			ScheduledCallRepository scheduledCallRepository,
			CallInviteeRepository callInviteeRepository,
			CallLogRepository callLogRepository,
			CallAuthorizationService callAuthorizationService,
			CallEventPublisher eventPublisher,
			JitsiBotService jitsiBotService,
			PlatformTransactionManager transactionManager) {
		this.scheduledCallRepository = scheduledCallRepository;
		this.callInviteeRepository = callInviteeRepository;
		this.callLogRepository = callLogRepository;
		this.callAuthorizationService = callAuthorizationService;
		this.eventPublisher = eventPublisher;
		this.jitsiBotService = jitsiBotService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	@Transactional
	public ScheduledCall create(AuthPrincipal principal, ScheduleCallRequest request) {
		UUID schoolId = principal.getSchoolId();
		for (UUID inviteeId : request.getInviteeOwnerIds()) {
			callAuthorizationService.requireExists(schoolId, request.getInviteeOwnerType(), inviteeId);
			callAuthorizationService.requireCanCall(principal, request.getInviteeOwnerType(), inviteeId);
		}

		ScheduledCall call = new ScheduledCall();
		call.setSchoolId(schoolId);
		call.setHostOwnerType(principal.getOwnerType());
		call.setHostOwnerId(principal.getOwnerId());
		call.setTitle(request.getTitle());
		call.setScheduledAt(request.getScheduledAt());
		call.setRoomName(RoomNames.generate());
		call.setStatus(CallStatus.SCHEDULED);
		call.setReminderSent(false);
		call = scheduledCallRepository.save(call);

		for (UUID inviteeId : request.getInviteeOwnerIds()) {
			CallInvitee invitee = new CallInvitee();
			invitee.setSchoolId(schoolId);
			invitee.setScheduledCall(call);
			invitee.setOwnerType(request.getInviteeOwnerType());
			invitee.setOwnerId(inviteeId);
			invitee.setRsvpStatus(RsvpStatus.PENDING);
			callInviteeRepository.save(invitee);
		}
		return call;
	}

	public List<ScheduledCall> hostedBy(AuthPrincipal principal) {
		return scheduledCallRepository.findAllBySchoolIdAndHostOwnerTypeAndHostOwnerIdOrderByScheduledAtDesc(
				principal.getSchoolId(), principal.getOwnerType(), principal.getOwnerId());
	}

	/**
	 * Builds MyInviteResponse here (not in the controller) because it dereferences
	 * CallInvitee.scheduledCall - a LAZY association - and open-in-view is disabled, so that
	 * dereference must happen while this method's own transaction is still open.
	 */
	@Transactional(readOnly = true)
	public List<MyInviteResponse> invitedTo(AuthPrincipal principal) {
		return callInviteeRepository.findAllBySchoolIdAndOwnerTypeAndOwnerIdOrderByCreatedAtDesc(
						principal.getSchoolId(), principal.getOwnerType(), principal.getOwnerId())
				.stream()
				.map(MyInviteResponse::from)
				.toList();
	}

	public List<CallInvitee> invitees(UUID scheduledCallId) {
		return callInviteeRepository.findAllByScheduledCall_Id(scheduledCallId);
	}

	public ScheduledCall requireScheduled(UUID schoolId, UUID scheduledCallId) {
		return scheduledCallRepository.findByIdAndSchoolId(scheduledCallId, schoolId)
				.orElseThrow(() -> new EntityNotFoundException("Scheduled call not found"));
	}

	/** Visible to its host, or to any of its invitees - nobody else. */
	public ScheduledCall requireVisible(AuthPrincipal principal, UUID scheduledCallId) {
		ScheduledCall call = requireScheduled(principal.getSchoolId(), scheduledCallId);
		boolean isHost = call.getHostOwnerType() == principal.getOwnerType() && call.getHostOwnerId().equals(principal.getOwnerId());
		boolean isInvitee = callInviteeRepository.existsByScheduledCall_IdAndOwnerTypeAndOwnerId(
				scheduledCallId, principal.getOwnerType(), principal.getOwnerId());
		if (!isHost && !isInvitee) {
			throw new AccessDeniedException("You do not have visibility into this call");
		}
		return call;
	}

	@Transactional
	public CallInvitee rsvp(AuthPrincipal principal, UUID scheduledCallId, RsvpStatus status) {
		if (status == RsvpStatus.PENDING) {
			throw new IllegalArgumentException("RSVP must be ACCEPTED or DECLINED");
		}
		requireScheduled(principal.getSchoolId(), scheduledCallId);
		CallInvitee invitee = callInviteeRepository
				.findByScheduledCall_IdAndOwnerTypeAndOwnerId(scheduledCallId, principal.getOwnerType(), principal.getOwnerId())
				.orElseThrow(() -> new AccessDeniedException("You are not invited to this call"));
		invitee.setRsvpStatus(status);
		return callInviteeRepository.save(invitee);
	}

	@Transactional
	public ScheduledCall cancel(AuthPrincipal principal, UUID scheduledCallId) {
		ScheduledCall call = requireHost(principal, scheduledCallId);
		if (call.getStatus() != CallStatus.SCHEDULED) {
			throw new IllegalStateException("Only a still-scheduled call can be cancelled");
		}
		call.setStatus(CallStatus.CANCELLED);
		scheduledCallRepository.save(call);
		notifyInvitees(call, CallEvent.Type.CALL_CANCELLED);
		return call;
	}

	public ScheduledCall start(AuthPrincipal principal, UUID scheduledCallId) {
		ScheduledCall call = requireHost(principal, scheduledCallId);
		if (call.getStatus() != CallStatus.SCHEDULED) {
			throw new IllegalStateException("This call cannot be started (status: " + call.getStatus() + ")");
		}

		// Warmed before any DB writes or the invitee notifications below - otherwise a fast joiner
		// could beat the bot into the room. Deliberately run outside a transaction: this call to
		// Jitsi's server can take up to warmTimeoutSeconds, and must not hold a DB connection open
		// for that long. A failure here means invitees would otherwise land on Jitsi's login wall,
		// so we refuse to start the session instead of proceeding.
		if (!jitsiBotService.warmRoom(call.getRoomName())) {
			throw new IllegalStateException("Could not start the call right now - please try again");
		}

		transactionTemplate.executeWithoutResult(status -> {
			call.setStatus(CallStatus.STARTED);
			scheduledCallRepository.save(call);

			CallLog log = new CallLog();
			log.setSchoolId(call.getSchoolId());
			log.setScheduledCallId(call.getId());
			log.setCallerOwnerType(call.getHostOwnerType());
			log.setCallerOwnerId(call.getHostOwnerId());
			log.setRoomName(call.getRoomName());
			log.setStartedAt(Instant.now());
			log.setOutcome(CallOutcome.IN_PROGRESS);
			callLogRepository.save(log);
		});

		callInviteeRepository.findAllByScheduledCall_IdAndRsvpStatus(call.getId(), RsvpStatus.ACCEPTED)
				.forEach(invitee -> eventPublisher.sendTo(call.getSchoolId(), invitee.getOwnerType(), invitee.getOwnerId(),
						CallEvent.scheduledStarted(call.getId(), call.getRoomName(), call.getTitle())));
		return call;
	}

	@Transactional
	public ScheduledCall endSession(AuthPrincipal principal, UUID scheduledCallId) {
		ScheduledCall call = requireHost(principal, scheduledCallId);
		if (call.getStatus() == CallStatus.STARTED) {
			call.setStatus(CallStatus.COMPLETED);
			scheduledCallRepository.save(call);
		}
		return call;
	}

	private ScheduledCall requireHost(AuthPrincipal principal, UUID scheduledCallId) {
		ScheduledCall call = requireScheduled(principal.getSchoolId(), scheduledCallId);
		if (call.getHostOwnerType() != principal.getOwnerType() || !call.getHostOwnerId().equals(principal.getOwnerId())) {
			throw new AccessDeniedException("Only the host can do this");
		}
		return call;
	}

	private void notifyInvitees(ScheduledCall call, CallEvent.Type type) {
		invitees(call.getId()).forEach(invitee -> eventPublisher.sendTo(call.getSchoolId(),
				invitee.getOwnerType(), invitee.getOwnerId(),
				new CallEvent(type, null, call.getId(), call.getRoomName(), null, null, call.getTitle(), call.getScheduledAt())));
	}

	/** Every minute: send the 30-minute-out reminder once per call, and auto-expire no-shows. */
	@Scheduled(fixedRate = 60_000)
	@Transactional
	public void runReminderAndExpirySweep() {
		Instant now = Instant.now();

		scheduledCallRepository
				.findAllByStatusAndReminderSentFalseAndScheduledAtLessThanEqual(CallStatus.SCHEDULED, now.plus(REMINDER_LEAD))
				.forEach(call -> {
					call.setReminderSent(true);
					scheduledCallRepository.save(call);
					eventPublisher.sendTo(call.getSchoolId(), call.getHostOwnerType(), call.getHostOwnerId(),
							CallEvent.scheduledReminder(call.getId(), call.getTitle(), call.getScheduledAt()));
					callInviteeRepository.findAllByScheduledCall_IdAndRsvpStatus(call.getId(), RsvpStatus.ACCEPTED)
							.forEach(invitee -> eventPublisher.sendTo(call.getSchoolId(), invitee.getOwnerType(), invitee.getOwnerId(),
									CallEvent.scheduledReminder(call.getId(), call.getTitle(), call.getScheduledAt())));
				});

		scheduledCallRepository.findAllByStatusAndScheduledAtBefore(CallStatus.SCHEDULED, now.minus(EXPIRE_GRACE))
				.forEach(call -> {
					call.setStatus(CallStatus.EXPIRED);
					scheduledCallRepository.save(call);
				});
	}

}
