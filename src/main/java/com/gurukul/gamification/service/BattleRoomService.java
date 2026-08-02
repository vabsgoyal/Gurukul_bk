package com.gurukul.gamification.service;

import com.gurukul.academics.entity.Subject;
import com.gurukul.academics.repository.SubjectRepository;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.gamification.dto.ArenaDtos.PublicQuizQuestionResponse;
import com.gurukul.gamification.dto.BattleRoomDtos.BattleParticipantResponse;
import com.gurukul.gamification.dto.BattleRoomDtos.BattleRoomResponse;
import com.gurukul.gamification.dto.BattleRoomDtos.BattleRoomSummaryResponse;
import com.gurukul.gamification.dto.BattleRoomDtos.BuzzResponse;
import com.gurukul.gamification.dto.BattleRoomDtos.CreateBattleRoomRequest;
import com.gurukul.gamification.dto.BattleRoomDtos.MatchBattleRoomRequest;
import com.gurukul.gamification.dto.BattleRoomDtos.SubmitBattleAnswerRequest;
import com.gurukul.gamification.dto.BattleRoomDtos.SubmitBattleAnswerResponse;
import com.gurukul.gamification.entity.BattleAnswer;
import com.gurukul.gamification.entity.BattleBuzzWinner;
import com.gurukul.gamification.entity.BattleRoom;
import com.gurukul.gamification.entity.BattleRoomParticipant;
import com.gurukul.gamification.entity.BattleRoomStatus;
import com.gurukul.gamification.entity.QuizQuestion;
import com.gurukul.gamification.entity.XpSource;
import com.gurukul.gamification.repository.BattleAnswerRepository;
import com.gurukul.gamification.repository.BattleBuzzWinnerRepository;
import com.gurukul.gamification.repository.BattleRoomParticipantRepository;
import com.gurukul.gamification.repository.BattleRoomRepository;
import com.gurukul.gamification.repository.QuizQuestionRepository;
import com.gurukul.students.entity.Student;
import com.gurukul.students.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Gamification Phase 4b: Battle Rooms - live, multiplayer (2-5 students) fastest-buzz-first quiz
 * battles, scoped to one class (any section) + one subject. Unlike Arena (Phase 4a, async 1v1),
 * this needs real-time signaling, so state changes are both returned from REST/STOMP handlers and
 * broadcast to /topic/battle-rooms/{roomId} so every connected participant stays in sync.
 *
 * Room lifecycle is driven by the scheduled sweep() below, not per-room timers/actors - same
 * poll-based pattern ArenaService already uses for expiring stale challenges.
 */
@Service
@RequiredArgsConstructor
public class BattleRoomService {

	// Excludes 0/O and 1/I to avoid read-aloud/typing ambiguity for students sharing a code.
	private static final String ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final int ROOM_CODE_LENGTH = 6;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final BattleRoomRepository battleRoomRepository;
	private final BattleRoomParticipantRepository participantRepository;
	private final BattleBuzzWinnerRepository buzzWinnerRepository;
	private final BattleAnswerRepository battleAnswerRepository;
	private final QuizQuestionRepository quizQuestionRepository;
	private final SubjectRepository subjectRepository;
	private final StudentRepository studentRepository;
	private final GamificationService gamificationService;
	private final SimpMessagingTemplate messagingTemplate;

	@Value("${app.gamification.battle-room.min-players:2}")
	private int defaultMinPlayers;

	@Value("${app.gamification.battle-room.max-players:5}")
	private int defaultMaxPlayers;

	@Value("${app.gamification.battle-room.join-window-seconds:60}")
	private int defaultJoinWindowSeconds;

	@Value("${app.gamification.battle-room.question-count:10}")
	private int defaultQuestionCount;

	@Value("${app.gamification.battle-room.question-timeout-seconds:15}")
	private int questionTimeoutSeconds;

	@Value("${app.gamification.battle-room.win-xp:10}")
	private int winXp;

	@Transactional
	public BattleRoomResponse createRoom(AuthPrincipal principal, CreateBattleRoomRequest request) {
		return createRoomInternal(principal, request.getSubjectId());
	}

	@Transactional
	public BattleRoomResponse matchRoom(AuthPrincipal principal, MatchBattleRoomRequest request) {
		requireStudent(principal);
		Student student = requireOwnStudent(principal);
		List<BattleRoom> openRooms = battleRoomRepository
				.findAllBySchoolIdAndClassNameAndAcademicYearAndSubjectIdAndStatusOrderByCreatedAtAsc(
						principal.getSchoolId(), student.getClassSection().getClassName(),
						student.getClassSection().getAcademicYear(), request.getSubjectId(), BattleRoomStatus.WAITING);

		for (BattleRoom room : openRooms) {
			if (participantRepository.countByRoomId(room.getId()) < room.getMaxPlayers()) {
				return joinRoom(principal, room.getId());
			}
		}
		return createRoomInternal(principal, request.getSubjectId());
	}

	private BattleRoomResponse createRoomInternal(AuthPrincipal principal, UUID subjectId) {
		requireStudent(principal);
		Student creator = requireOwnStudent(principal);
		Subject subject = subjectRepository.findByIdAndSchoolId(subjectId, principal.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Subject not found"));

		BattleRoom room = new BattleRoom();
		room.setSchoolId(principal.getSchoolId());
		room.setClassName(creator.getClassSection().getClassName());
		room.setAcademicYear(creator.getClassSection().getAcademicYear());
		room.setSubject(subject);
		room.setCreatedByStudentId(creator.getId());
		room.setStatus(BattleRoomStatus.WAITING);
		room.setMinPlayers(defaultMinPlayers);
		room.setMaxPlayers(defaultMaxPlayers);
		room.setJoinWindowSeconds(defaultJoinWindowSeconds);
		room.setQuestionCount(defaultQuestionCount);
		room.setCurrentQuestionIndex(0);
		room.setRoomCode(generateRoomCode(principal.getSchoolId()));
		room = battleRoomRepository.save(room);

		addParticipant(room, creator.getId());
		broadcast(room);
		return buildResponse(room);
	}

	@Transactional
	public BattleRoomResponse joinRoom(AuthPrincipal principal, UUID roomId) {
		requireStudent(principal);
		BattleRoom room = battleRoomRepository.findByIdAndSchoolId(roomId, principal.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Battle room not found"));

		if (participantRepository.existsByRoomIdAndStudentId(roomId, principal.getOwnerId())) {
			return buildResponse(room);
		}
		if (room.getStatus() != BattleRoomStatus.WAITING) {
			throw new IllegalStateException("This room is no longer accepting players");
		}

		Student student = requireOwnStudent(principal);
		if (!student.getClassSection().getClassName().equals(room.getClassName())
				|| !student.getClassSection().getAcademicYear().equals(room.getAcademicYear())) {
			throw new IllegalArgumentException("This battle is for " + room.getClassName() + " students only");
		}
		if (participantRepository.countByRoomId(roomId) >= room.getMaxPlayers()) {
			throw new IllegalStateException("This room is full");
		}

		addParticipant(room, student.getId());
		broadcast(room);
		return buildResponse(room);
	}

	@Transactional
	public BattleRoomResponse joinByCode(AuthPrincipal principal, String code) {
		BattleRoom room = battleRoomRepository.findBySchoolIdAndRoomCode(principal.getSchoolId(), code.trim().toUpperCase())
				.orElseThrow(() -> new EntityNotFoundException("No room found for that code"));
		return joinRoom(principal, room.getId());
	}

	@Transactional(readOnly = true)
	public BattleRoomResponse getRoom(AuthPrincipal principal, UUID roomId) {
		requireStudent(principal);
		return buildResponse(requireParticipant(principal, roomId));
	}

	/**
	 * Browse list for a student's own class: every WAITING/ACTIVE room (COMPLETED/CANCELLED ones
	 * are never worth listing here), optionally filtered to one subject. Class/academicYear are
	 * derived from the caller's own enrollment, same as everywhere else in this service - never
	 * client-supplied.
	 */
	@Transactional(readOnly = true)
	public List<BattleRoomSummaryResponse> listBrowsableRooms(AuthPrincipal principal, UUID subjectId) {
		requireStudent(principal);
		Student student = requireOwnStudent(principal);
		List<BattleRoom> rooms = battleRoomRepository.findBrowsableRoomsForClass(
				principal.getSchoolId(), student.getClassSection().getClassName(),
				student.getClassSection().getAcademicYear(), subjectId);

		return rooms.stream()
				.map(room -> new BattleRoomSummaryResponse(
						room.getId(), room.getRoomCode(), room.getSubject().getName(), room.getClassName(),
						room.getStatus(), (int) participantRepository.countByRoomId(room.getId()), room.getMaxPlayers()))
				.toList();
	}

	@Transactional
	public BuzzResponse buzz(AuthPrincipal principal, UUID roomId) {
		requireStudent(principal);
		BattleRoom room = requireParticipant(principal, roomId);
		if (room.getStatus() != BattleRoomStatus.ACTIVE) {
			throw new IllegalStateException("This battle isn't live");
		}

		BattleBuzzWinner buzz = new BattleBuzzWinner();
		buzz.setSchoolId(principal.getSchoolId());
		buzz.setRoomId(roomId);
		buzz.setQuestionIndex(room.getCurrentQuestionIndex());
		buzz.setStudentId(principal.getOwnerId());
		buzz.setBuzzedAt(Instant.now());
		try {
			// saveAndFlush forces the constraint check now, inside this request's own transaction -
			// whichever concurrent buzz's INSERT commits first wins uq_battle_buzz_winner, the rest
			// fail it and land in the catch below. No in-memory locking needed.
			buzzWinnerRepository.saveAndFlush(buzz);
		} catch (DataIntegrityViolationException ex) {
			return new BuzzResponse(false);
		}

		broadcast(room);
		return new BuzzResponse(true);
	}

	@Transactional
	public SubmitBattleAnswerResponse submitAnswer(AuthPrincipal principal, UUID roomId, SubmitBattleAnswerRequest request) {
		requireStudent(principal);
		BattleRoom room = requireParticipant(principal, roomId);
		if (room.getStatus() != BattleRoomStatus.ACTIVE) {
			throw new IllegalStateException("This battle isn't live");
		}
		int index = room.getCurrentQuestionIndex();
		BattleBuzzWinner winner = buzzWinnerRepository.findByRoomIdAndQuestionIndex(roomId, index)
				.orElseThrow(() -> new IllegalStateException("Nobody has buzzed in for this question yet"));
		if (!winner.getStudentId().equals(principal.getOwnerId())) {
			throw new AccessDeniedException("It's not your turn to answer");
		}
		if (battleAnswerRepository.existsByRoomIdAndQuestionIndex(roomId, index)) {
			throw new IllegalStateException("This question has already been answered");
		}

		UUID questionId = room.questionIdList().get(index);
		QuizQuestion question = quizQuestionRepository.findById(questionId)
				.orElseThrow(() -> new EntityNotFoundException("Question not found"));
		boolean correct = question.getCorrectOption() == request.getSelectedOption();

		BattleAnswer answer = new BattleAnswer();
		answer.setSchoolId(principal.getSchoolId());
		answer.setRoomId(roomId);
		answer.setQuestionIndex(index);
		answer.setStudentId(principal.getOwnerId());
		answer.setSelectedOption(request.getSelectedOption());
		answer.setCorrect(correct);
		battleAnswerRepository.save(answer);

		if (correct) {
			BattleRoomParticipant participant = participantRepository.findByRoomIdAndStudentId(roomId, principal.getOwnerId())
					.orElseThrow(() -> new EntityNotFoundException("Participant not found"));
			participant.setCorrectCount(participant.getCorrectCount() + 1);
			participantRepository.save(participant);
		}

		advanceOrComplete(room);
		broadcast(room);
		return new SubmitBattleAnswerResponse(correct, room.getStatus() == BattleRoomStatus.COMPLETED);
	}

	/** Every 5s: start rooms whose join window elapsed, cancel lonely ones, and skip questions nobody answered in time. */
	@Scheduled(fixedRate = 5000)
	@Transactional
	public void sweep() {
		sweepWaitingRooms();
		sweepActiveRoomTimeouts();
	}

	private void sweepWaitingRooms() {
		Instant now = Instant.now();
		for (BattleRoom room : battleRoomRepository.findAllByStatus(BattleRoomStatus.WAITING)) {
			if (now.isBefore(room.getCreatedAt().plusSeconds(room.getJoinWindowSeconds()))) {
				continue;
			}
			if (participantRepository.countByRoomId(room.getId()) >= 2) {
				activateRoom(room);
			} else {
				room.setStatus(BattleRoomStatus.CANCELLED);
				battleRoomRepository.save(room);
				broadcast(room);
			}
		}
	}

	private void sweepActiveRoomTimeouts() {
		Instant now = Instant.now();
		for (BattleRoom room : battleRoomRepository.findAllByStatus(BattleRoomStatus.ACTIVE)) {
			if (room.getQuestionStartedAt() == null
					|| now.isBefore(room.getQuestionStartedAt().plusSeconds(questionTimeoutSeconds))) {
				continue;
			}
			if (battleAnswerRepository.existsByRoomIdAndQuestionIndex(room.getId(), room.getCurrentQuestionIndex())) {
				continue;
			}
			advanceOrComplete(room);
			broadcast(room);
		}
	}

	private void activateRoom(BattleRoom room) {
		List<QuizQuestion> pool = new ArrayList<>(
				quizQuestionRepository.findAllBySchoolIdAndSubjectId(room.getSchoolId(), room.getSubject().getId()));
		if (pool.isEmpty()) {
			room.setStatus(BattleRoomStatus.CANCELLED);
			battleRoomRepository.save(room);
			broadcast(room);
			return;
		}
		Collections.shuffle(pool, new SecureRandom());
		int actualCount = Math.min(room.getQuestionCount(), pool.size());
		List<QuizQuestion> picked = pool.subList(0, actualCount);

		room.setQuestionIds(BattleRoom.joinQuestionIds(picked.stream().map(QuizQuestion::getId).toList()));
		room.setQuestionCount(actualCount);
		room.setStatus(BattleRoomStatus.ACTIVE);
		room.setCurrentQuestionIndex(0);
		room.setQuestionStartedAt(Instant.now());
		battleRoomRepository.save(room);
		broadcast(room);
	}

	/** Advances to the next question, or completes the room if the current one was the last. */
	private void advanceOrComplete(BattleRoom room) {
		int nextIndex = room.getCurrentQuestionIndex() + 1;
		if (nextIndex >= room.getQuestionCount()) {
			completeRoom(room);
		} else {
			room.setCurrentQuestionIndex(nextIndex);
			room.setQuestionStartedAt(Instant.now());
			battleRoomRepository.save(room);
		}
	}

	/**
	 * Winner is whoever answered the most questions correctly. Ties go to whoever joined first -
	 * a provisional rule, not yet a settled business decision (difficulty-weighted scoring is a
	 * documented future phase per the gamification execution plan).
	 */
	private void completeRoom(BattleRoom room) {
		List<BattleRoomParticipant> participants = participantRepository.findAllByRoomIdOrderByJoinedAtAsc(room.getId());
		BattleRoomParticipant winner = participants.stream()
				.max((a, b) -> Integer.compare(a.getCorrectCount(), b.getCorrectCount()))
				.orElse(null);

		room.setStatus(BattleRoomStatus.COMPLETED);
		if (winner != null) {
			room.setWinnerStudentId(winner.getStudentId());
		}
		battleRoomRepository.save(room);

		if (winner != null) {
			gamificationService.awardXp(room.getSchoolId(), winner.getStudentId(), XpSource.BATTLE_ROOM_WIN, winXp);
		}
	}

	private String generateRoomCode(UUID schoolId) {
		for (int attempt = 0; attempt < 10; attempt++) {
			StringBuilder code = new StringBuilder(ROOM_CODE_LENGTH);
			for (int i = 0; i < ROOM_CODE_LENGTH; i++) {
				code.append(ROOM_CODE_ALPHABET.charAt(RANDOM.nextInt(ROOM_CODE_ALPHABET.length())));
			}
			String candidate = code.toString();
			if (battleRoomRepository.findBySchoolIdAndRoomCode(schoolId, candidate).isEmpty()) {
				return candidate;
			}
		}
		throw new IllegalStateException("Could not generate a unique room code, please try again");
	}

	private void addParticipant(BattleRoom room, UUID studentId) {
		BattleRoomParticipant participant = new BattleRoomParticipant();
		participant.setSchoolId(room.getSchoolId());
		participant.setRoomId(room.getId());
		participant.setStudentId(studentId);
		participant.setJoinedAt(Instant.now());
		participantRepository.save(participant);
	}

	private void broadcast(BattleRoom room) {
		messagingTemplate.convertAndSend("/topic/battle-rooms/" + room.getId(), buildResponse(room));
	}

	private BattleRoomResponse buildResponse(BattleRoom room) {
		List<BattleParticipantResponse> participants = participantRepository
				.findAllByRoomIdOrderByJoinedAtAsc(room.getId()).stream()
				.map(p -> new BattleParticipantResponse(p.getStudentId(), studentName(room.getSchoolId(), p.getStudentId()), p.getCorrectCount()))
				.toList();

		PublicQuizQuestionResponse currentQuestion = null;
		UUID currentBuzzWinnerStudentId = null;
		if (room.getStatus() == BattleRoomStatus.ACTIVE) {
			List<UUID> questionIds = room.questionIdList();
			if (room.getCurrentQuestionIndex() < questionIds.size()) {
				currentQuestion = quizQuestionRepository.findById(questionIds.get(room.getCurrentQuestionIndex()))
						.map(PublicQuizQuestionResponse::from).orElse(null);
			}
			currentBuzzWinnerStudentId = buzzWinnerRepository
					.findByRoomIdAndQuestionIndex(room.getId(), room.getCurrentQuestionIndex())
					.map(BattleBuzzWinner::getStudentId).orElse(null);
		}

		Boolean lastAnswerCorrect = null;
		int previousIndex = room.getCurrentQuestionIndex() - 1;
		if (previousIndex >= 0) {
			lastAnswerCorrect = battleAnswerRepository.findByRoomIdAndQuestionIndex(room.getId(), previousIndex)
					.map(BattleAnswer::isCorrect).orElse(null);
		}

		String winnerName = room.getWinnerStudentId() != null
				? studentName(room.getSchoolId(), room.getWinnerStudentId())
				: null;

		Instant joinWindowEndsAt = room.getCreatedAt().plusSeconds(room.getJoinWindowSeconds());

		return new BattleRoomResponse(
				room.getId(), room.getRoomCode(), room.getClassName(), room.getSubject().getName(), room.getStatus(),
				room.getMinPlayers(), room.getMaxPlayers(), room.getJoinWindowSeconds(), joinWindowEndsAt,
				room.getQuestionCount(), room.getCurrentQuestionIndex(), participants, currentQuestion,
				currentBuzzWinnerStudentId, lastAnswerCorrect, room.getWinnerStudentId(), winnerName);
	}

	private String studentName(UUID schoolId, UUID studentId) {
		return studentRepository.findByIdAndSchoolId(studentId, schoolId).map(Student::getName).orElse("Unknown");
	}

	/** Used by StompSubscribeAuthorizationInterceptor to gate /topic/battle-rooms/{roomId} subscriptions. */
	public boolean isParticipant(AuthPrincipal principal, UUID roomId) {
		return battleRoomRepository.findByIdAndSchoolId(roomId, principal.getSchoolId())
				.map(room -> participantRepository.existsByRoomIdAndStudentId(roomId, principal.getOwnerId()))
				.orElse(false);
	}

	private BattleRoom requireParticipant(AuthPrincipal principal, UUID roomId) {
		BattleRoom room = battleRoomRepository.findByIdAndSchoolId(roomId, principal.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Battle room not found"));
		if (!participantRepository.existsByRoomIdAndStudentId(roomId, principal.getOwnerId())) {
			throw new AccessDeniedException("You are not part of this battle room");
		}
		return room;
	}

	private Student requireOwnStudent(AuthPrincipal principal) {
		return studentRepository.findByIdAndSchoolId(principal.getOwnerId(), principal.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Student not found"));
	}

	private void requireStudent(AuthPrincipal principal) {
		if (principal.getOwnerType() != OwnerType.STUDENT) {
			throw new AccessDeniedException("Only a student account can join Battle Rooms");
		}
	}

}
