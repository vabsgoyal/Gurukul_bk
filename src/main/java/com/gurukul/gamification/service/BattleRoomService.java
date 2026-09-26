package com.gurukul.gamification.service;

import com.gurukul.academics.entity.Subject;
import com.gurukul.academics.repository.SubjectRepository;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.gamification.dto.ArenaDtos.PublicQuizQuestionResponse;
import com.gurukul.gamification.dto.BattleRoomDtos.BattleParticipantResponse;
import com.gurukul.gamification.dto.BattleRoomDtos.BattlePlayerResultResponse;
import com.gurukul.gamification.dto.BattleRoomDtos.BattleQuestionResultResponse;
import com.gurukul.gamification.dto.BattleRoomDtos.BattleRoomResponse;
import com.gurukul.gamification.dto.BattleRoomDtos.BattleRoomSummaryResponse;
import com.gurukul.gamification.dto.BattleRoomDtos.CreateBattleRoomRequest;
import com.gurukul.gamification.dto.BattleRoomDtos.MatchBattleRoomRequest;
import com.gurukul.gamification.dto.BattleRoomDtos.SubmitBattleAnswerRequest;
import com.gurukul.gamification.dto.BattleRoomDtos.SubmitBattleAnswerResponse;
import com.gurukul.gamification.entity.BattleAnswer;
import com.gurukul.gamification.entity.BattleRoom;
import com.gurukul.gamification.entity.BattleRoomParticipant;
import com.gurukul.gamification.entity.BattleRoomStatus;
import com.gurukul.gamification.entity.QuizOption;
import com.gurukul.gamification.entity.QuizQuestion;
import com.gurukul.gamification.entity.XpSource;
import com.gurukul.gamification.repository.BattleAnswerRepository;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Gamification Phase 4b: Battle Rooms - live, multiplayer (2-5 students) quiz battles, scoped to
 * one class (any section) + one subject. Every participant answers every question within the answer
 * window; a correct answer scores 1-10 by how fast it arrived (server-measured), a wrong one 0. Unlike Arena (Phase 4a, async 1v1),
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
	/** Points for a correct answer in the first second; one less per full second after, never below 1. */
	private static final int MAX_POINTS = 10;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final BattleRoomRepository battleRoomRepository;
	private final BattleRoomParticipantRepository participantRepository;
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

	/** How long everyone has to answer each question - the room moves on sooner if all have answered. */
	@Value("${app.gamification.battle-room.question-timeout-seconds:10}")
	private int questionTimeoutSeconds;

	@Value("${app.gamification.battle-room.win-xp:10}")
	private int winXp;

	@Value("${app.gamification.battle-room.reveal-seconds:3}")
	private int revealSeconds;

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
	 * Lets any participant skip the rest of the join window once minPlayers is met, instead of
	 * waiting for sweep() to do it on the next 5s tick. Same activateRoom() path as the scheduled
	 * sweep, so there's exactly one way a room actually goes ACTIVE - no separate "host" concept
	 * needed since any participant may trigger it.
	 */
	@Transactional
	public BattleRoomResponse startRoomNow(AuthPrincipal principal, UUID roomId) {
		requireStudent(principal);
		BattleRoom room = requireParticipant(principal, roomId);
		if (room.getStatus() != BattleRoomStatus.WAITING) {
			throw new IllegalStateException("This room is no longer waiting to start");
		}
		long count = participantRepository.countByRoomId(roomId);
		if (count < room.getMinPlayers()) {
			throw new IllegalArgumentException("Need at least " + room.getMinPlayers() + " players to start");
		}
		activateRoom(room);
		return buildResponse(room);
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

	/**
	 * Any participant may answer the current question once, between its start (after the reveal pause)
	 * and its deadline. Only confirms the answer was locked in - correctness is revealed to everyone
	 * in lastResult once the question closes, which happens as soon as the last participant answers.
	 */
	@Transactional
	public SubmitBattleAnswerResponse submitAnswer(AuthPrincipal principal, UUID roomId, SubmitBattleAnswerRequest request) {
		requireStudent(principal);
		// Lock before anything else reads the room, so every check below sees the committed row.
		BattleRoom room = lockRoom(roomId);
		if (!room.getSchoolId().equals(principal.getSchoolId())) {
			throw new EntityNotFoundException("Battle room not found");
		}
		if (!participantRepository.existsByRoomIdAndStudentId(roomId, principal.getOwnerId())) {
			throw new AccessDeniedException("You are not part of this battle room");
		}
		if (room.getStatus() != BattleRoomStatus.ACTIVE) {
			throw new IllegalStateException("This battle isn't live");
		}
		Instant now = Instant.now();
		Instant startsAt = room.getQuestionStartedAt();
		if (now.isBefore(startsAt)) {
			throw new IllegalStateException("The next question hasn't started yet");
		}
		if (!now.isBefore(questionDeadline(room))) {
			throw new IllegalStateException("Time's up for this question");
		}
		int index = room.getCurrentQuestionIndex();
		if (battleAnswerRepository.existsByRoomIdAndQuestionIndexAndStudentId(roomId, index, principal.getOwnerId())) {
			throw new IllegalStateException("You've already answered this question");
		}

		UUID questionId = room.questionIdList().get(index);
		QuizQuestion question = quizQuestionRepository.findById(questionId)
				.orElseThrow(() -> new EntityNotFoundException("Question not found"));
		boolean correct = question.getCorrectOption() == request.getSelectedOption();
		int responseMs = (int) Duration.between(startsAt, now).toMillis();
		int points = correct ? pointsFor(responseMs) : 0;

		BattleAnswer answer = new BattleAnswer();
		answer.setSchoolId(principal.getSchoolId());
		answer.setRoomId(roomId);
		answer.setQuestionIndex(index);
		answer.setStudentId(principal.getOwnerId());
		answer.setSelectedOption(request.getSelectedOption());
		answer.setCorrect(correct);
		answer.setPoints(points);
		answer.setResponseMs(responseMs);
		battleAnswerRepository.save(answer);

		if (battleAnswerRepository.countByRoomIdAndQuestionIndex(roomId, index) >= participantRepository.countByRoomId(roomId)) {
			advanceOrComplete(room);
		}
		broadcast(room);
		return new SubmitBattleAnswerResponse(index, room.getStatus() == BattleRoomStatus.COMPLETED);
	}

	static int pointsFor(int responseMs) {
		return Math.max(1, MAX_POINTS - responseMs / 1000);
	}

	/**
	 * Every second: start rooms whose join window elapsed, cancel lonely ones, and close questions
	 * whose answer window ran out. Every second, not every 5, since the answer window is only 10s.
	 */
	@Scheduled(fixedRateString = "${app.gamification.battle-room.sweep-interval-ms:1000}")
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
		// Ids, then a fresh locked read per room - checking a room loaded before the lock could act on a
		// question the last answer already closed in the meantime.
		for (UUID roomId : battleRoomRepository.findIdsByStatus(BattleRoomStatus.ACTIVE)) {
			BattleRoom room = lockRoom(roomId);
			if (room.getStatus() != BattleRoomStatus.ACTIVE || room.getQuestionStartedAt() == null
					|| Instant.now().isBefore(questionDeadline(room))) {
				continue;
			}
			advanceOrComplete(room);
			broadcast(room);
		}
	}

	private Instant questionDeadline(BattleRoom room) {
		return room.getQuestionStartedAt().plusSeconds(questionTimeoutSeconds);
	}

	private BattleRoom lockRoom(UUID roomId) {
		return battleRoomRepository.findByIdForUpdate(roomId)
				.orElseThrow(() -> new EntityNotFoundException("Battle room not found"));
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

	/**
	 * Advances to the next question, or completes the room if the current one was the last. The next
	 * question starts revealSeconds from now, not immediately - that pause is when clients show the
	 * just-closed question's lastResult (everyone's answers, the correct option). submitAnswer()
	 * rejects until then, and the answer window runs from that start too.
	 */
	private void advanceOrComplete(BattleRoom room) {
		tallyCurrentQuestion(room);
		int nextIndex = room.getCurrentQuestionIndex() + 1;
		if (nextIndex >= room.getQuestionCount()) {
			completeRoom(room);
		} else {
			room.setCurrentQuestionIndex(nextIndex);
			room.setQuestionStartedAt(Instant.now().plusSeconds(revealSeconds));
			battleRoomRepository.save(room);
		}
	}

	/**
	 * Adds the closing question's points to each participant's total. Only done at close, never as
	 * answers come in - totals are visible to everyone, so a jump mid-question would give away who
	 * got it right before the reveal.
	 */
	private void tallyCurrentQuestion(BattleRoom room) {
		for (BattleAnswer answer : battleAnswerRepository.findAllByRoomIdAndQuestionIndex(room.getId(), room.getCurrentQuestionIndex())) {
			BattleRoomParticipant participant = participantRepository.findByRoomIdAndStudentId(room.getId(), answer.getStudentId())
					.orElseThrow(() -> new EntityNotFoundException("Participant not found"));
			participant.setPoints(participant.getPoints() + answer.getPoints());
			if (answer.isCorrect()) {
				participant.setCorrectCount(participant.getCorrectCount() + 1);
			}
			participantRepository.save(participant);
		}
	}

	/**
	 * Winner is whoever scored the most points. Ties go to more correct answers, then to whoever
	 * joined first - a provisional rule, not yet a settled business decision.
	 */
	private void completeRoom(BattleRoom room) {
		List<BattleRoomParticipant> participants = participantRepository.findAllByRoomIdOrderByJoinedAtAsc(room.getId());
		BattleRoomParticipant winner = null;
		for (BattleRoomParticipant p : participants) {
			if (winner == null || p.getPoints() > winner.getPoints()
					|| (p.getPoints() == winner.getPoints() && p.getCorrectCount() > winner.getCorrectCount())) {
				winner = p;
			}
		}

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
		List<BattleRoomParticipant> roster = participantRepository.findAllByRoomIdOrderByJoinedAtAsc(room.getId());
		Map<UUID, String> names = roster.stream()
				.collect(Collectors.toMap(BattleRoomParticipant::getStudentId, p -> studentName(room.getSchoolId(), p.getStudentId())));

		PublicQuizQuestionResponse currentQuestion = null;
		Instant currentQuestionStartsAt = null;
		Instant currentQuestionEndsAt = null;
		List<UUID> answeredCurrent = List.of();
		if (room.getStatus() == BattleRoomStatus.ACTIVE) {
			List<UUID> questionIds = room.questionIdList();
			if (room.getCurrentQuestionIndex() < questionIds.size()) {
				currentQuestion = quizQuestionRepository.findById(questionIds.get(room.getCurrentQuestionIndex()))
						.map(PublicQuizQuestionResponse::from).orElse(null);
			}
			currentQuestionStartsAt = room.getQuestionStartedAt();
			currentQuestionEndsAt = questionDeadline(room);
			answeredCurrent = battleAnswerRepository
					.findAllByRoomIdAndQuestionIndex(room.getId(), room.getCurrentQuestionIndex()).stream()
					.map(BattleAnswer::getStudentId).toList();
		}

		List<UUID> answered = answeredCurrent;
		List<BattleParticipantResponse> participants = roster.stream()
				.sorted(Comparator.comparingInt(BattleRoomParticipant::getPoints).reversed())
				.map(p -> new BattleParticipantResponse(p.getStudentId(), names.get(p.getStudentId()), p.getPoints(),
						p.getCorrectCount(), answered.contains(p.getStudentId())))
				.toList();

		String winnerName = room.getWinnerStudentId() != null ? names.get(room.getWinnerStudentId()) : null;
		Instant joinWindowEndsAt = room.getCreatedAt().plusSeconds(room.getJoinWindowSeconds());

		return new BattleRoomResponse(
				room.getId(), room.getRoomCode(), room.getClassName(), room.getSubject().getName(), room.getStatus(),
				room.getMinPlayers(), room.getMaxPlayers(), room.getJoinWindowSeconds(), joinWindowEndsAt,
				room.getQuestionCount(), room.getCurrentQuestionIndex(), participants, currentQuestion,
				currentQuestionStartsAt, currentQuestionEndsAt, buildLastResult(room, roster, names),
				room.getWinnerStudentId(), winnerName);
	}

	/**
	 * The most recently closed question: the one before the current while ACTIVE, or the final one
	 * once COMPLETED (completeRoom() leaves currentQuestionIndex on the last question). Revealing its
	 * correctOption and everyone's answers is safe - it's no longer in play. Participants with no
	 * BattleAnswer row didn't answer in time.
	 */
	private BattleQuestionResultResponse buildLastResult(BattleRoom room, List<BattleRoomParticipant> roster, Map<UUID, String> names) {
		int closedIndex;
		if (room.getStatus() == BattleRoomStatus.ACTIVE) {
			closedIndex = room.getCurrentQuestionIndex() - 1;
		} else if (room.getStatus() == BattleRoomStatus.COMPLETED) {
			closedIndex = room.getCurrentQuestionIndex();
		} else {
			return null;
		}
		List<UUID> questionIds = room.questionIdList();
		if (closedIndex < 0 || closedIndex >= questionIds.size()) {
			return null;
		}

		UUID questionId = questionIds.get(closedIndex);
		QuizOption correctOption = quizQuestionRepository.findById(questionId)
				.map(QuizQuestion::getCorrectOption).orElse(null);
		Map<UUID, BattleAnswer> answers = battleAnswerRepository.findAllByRoomIdAndQuestionIndex(room.getId(), closedIndex)
				.stream().collect(Collectors.toMap(BattleAnswer::getStudentId, Function.identity()));

		List<BattlePlayerResultResponse> results = roster.stream()
				.map(p -> {
					BattleAnswer a = answers.get(p.getStudentId());
					return a == null
							? new BattlePlayerResultResponse(p.getStudentId(), names.get(p.getStudentId()), false, null, false, 0, null)
							: new BattlePlayerResultResponse(p.getStudentId(), names.get(p.getStudentId()), true,
									a.getSelectedOption(), a.isCorrect(), a.getPoints(), a.getResponseMs());
				})
				.sorted(Comparator.comparingInt(BattlePlayerResultResponse::getPoints).reversed()
						.thenComparing(r -> r.getResponseMs() == null ? Integer.MAX_VALUE : r.getResponseMs()))
				.toList();

		return new BattleQuestionResultResponse(closedIndex, questionId, correctOption, results);
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
