package com.gurukul.gamification.service;

import com.gurukul.academics.entity.Subject;
import com.gurukul.academics.repository.SubjectRepository;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.employees.service.EmployeeService;
import com.gurukul.gamification.dto.ArenaDtos.ChallengeDetailResponse;
import com.gurukul.gamification.dto.ArenaDtos.ChallengeSummaryResponse;
import com.gurukul.gamification.dto.ArenaDtos.CreateChallengeRequest;
import com.gurukul.gamification.dto.ArenaDtos.CreateQuizQuestionRequest;
import com.gurukul.gamification.dto.ArenaDtos.PublicQuizQuestionResponse;
import com.gurukul.gamification.dto.ArenaDtos.QuizQuestionResponse;
import com.gurukul.gamification.dto.ArenaDtos.SubmitAnswerRequest;
import com.gurukul.gamification.dto.ArenaDtos.SubmitAnswerResponse;
import com.gurukul.gamification.entity.ChallengeStatus;
import com.gurukul.gamification.entity.QuizAnswer;
import com.gurukul.gamification.entity.QuizChallenge;
import com.gurukul.gamification.entity.QuizQuestion;
import com.gurukul.gamification.entity.XpSource;
import com.gurukul.gamification.repository.QuizAnswerRepository;
import com.gurukul.gamification.repository.QuizChallengeRepository;
import com.gurukul.gamification.repository.QuizQuestionRepository;
import com.gurukul.students.entity.Student;
import com.gurukul.students.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Gurukul Arena, Phase 4a (specs/gamification/execution-plan.md): async 1v1 quiz challenges
 * only. Each side answers in their own time - there's no live/synchronized session, so this is
 * pure REST with no new STOMP signaling needed. A live, class-wide, Kahoot-style quiz mode is a
 * documented fast-follow (Phase 4b) once this foundation - and an actual question bank, which
 * didn't exist anywhere in this codebase before this phase - is in place.
 */
@Service
@RequiredArgsConstructor
public class ArenaService {

	private static final int QUESTIONS_PER_CHALLENGE = 5;
	private static final int WIN_XP = 25;

	private final QuizQuestionRepository quizQuestionRepository;
	private final QuizChallengeRepository quizChallengeRepository;
	private final QuizAnswerRepository quizAnswerRepository;
	private final SubjectRepository subjectRepository;
	private final StudentRepository studentRepository;
	private final EmployeeService employeeService;
	private final GamificationService gamificationService;

	@Transactional
	public QuizQuestionResponse createQuestion(AuthPrincipal principal, CreateQuizQuestionRequest request) {
		if (principal.getRole() != Role.ADMIN && principal.getRole() != Role.TEACHER) {
			throw new AccessDeniedException("Only a teacher or admin can author quiz questions");
		}
		UUID schoolId = principal.getSchoolId();
		Subject subject = subjectRepository.findByIdAndSchoolId(request.getSubjectId(), schoolId)
				.orElseThrow(() -> new EntityNotFoundException("Subject not found"));

		QuizQuestion question = new QuizQuestion();
		question.setSchoolId(schoolId);
		question.setSubject(subject);
		question.setQuestionText(request.getQuestionText());
		question.setOptionA(request.getOptionA());
		question.setOptionB(request.getOptionB());
		question.setOptionC(request.getOptionC());
		question.setOptionD(request.getOptionD());
		question.setCorrectOption(request.getCorrectOption());
		question.setCreatedByTeacher(employeeService.getScopedEntity(principal.getOwnerId()));
		return QuizQuestionResponse.from(quizQuestionRepository.save(question));
	}

	public List<QuizQuestionResponse> listQuestions(AuthPrincipal principal, UUID subjectId) {
		if (principal.getRole() != Role.ADMIN && principal.getRole() != Role.TEACHER) {
			throw new AccessDeniedException("Only a teacher or admin can view the question bank");
		}
		return quizQuestionRepository.findAllBySchoolIdAndSubjectId(principal.getSchoolId(), subjectId).stream()
				.map(QuizQuestionResponse::from)
				.toList();
	}

	@Transactional
	public ChallengeSummaryResponse createChallenge(AuthPrincipal principal, CreateChallengeRequest request) {
		requireStudent(principal);
		UUID schoolId = principal.getSchoolId();
		UUID challengerId = principal.getOwnerId();
		if (challengerId.equals(request.getOpponentStudentId())) {
			throw new IllegalArgumentException("You can't challenge yourself");
		}

		Student challenger = studentRepository.findByIdAndSchoolId(challengerId, schoolId)
				.orElseThrow(() -> new EntityNotFoundException("Student not found"));
		Student opponent = studentRepository.findByIdAndSchoolId(request.getOpponentStudentId(), schoolId)
				.orElseThrow(() -> new EntityNotFoundException("Opponent not found"));
		if (!challenger.getClassSection().getId().equals(opponent.getClassSection().getId())) {
			throw new IllegalArgumentException("You can only challenge a classmate in your own class-section");
		}

		Subject subject = subjectRepository.findByIdAndSchoolId(request.getSubjectId(), schoolId)
				.orElseThrow(() -> new EntityNotFoundException("Subject not found"));

		List<QuizQuestion> pool = new ArrayList<>(quizQuestionRepository.findAllBySchoolIdAndSubjectId(schoolId, subject.getId()));
		if (pool.size() < QUESTIONS_PER_CHALLENGE) {
			throw new IllegalStateException(
					"Not enough " + subject.getName() + " questions yet (need at least " + QUESTIONS_PER_CHALLENGE + ")");
		}
		Collections.shuffle(pool, new SecureRandom());
		List<QuizQuestion> picked = pool.subList(0, QUESTIONS_PER_CHALLENGE);

		QuizChallenge challenge = new QuizChallenge();
		challenge.setSchoolId(schoolId);
		challenge.setSubject(subject);
		challenge.setChallengerStudentId(challengerId);
		challenge.setOpponentStudentId(opponent.getId());
		challenge.setStatus(ChallengeStatus.ACTIVE);
		challenge.setQuestionIds(QuizChallenge.joinQuestionIds(picked.stream().map(QuizQuestion::getId).toList()));
		challenge = quizChallengeRepository.save(challenge);

		return toSummary(challenge, principal);
	}

	public List<ChallengeSummaryResponse> listMyChallenges(AuthPrincipal principal) {
		requireStudent(principal);
		return quizChallengeRepository.findAllForStudent(principal.getSchoolId(), principal.getOwnerId()).stream()
				.map(c -> toSummary(c, principal))
				.toList();
	}

	public ChallengeDetailResponse getChallenge(AuthPrincipal principal, UUID challengeId) {
		requireStudent(principal);
		QuizChallenge challenge = requireParticipant(principal, challengeId);

		List<QuizQuestion> questions = quizQuestionRepository.findAllById(challenge.questionIdList());
		Map<UUID, QuizQuestion> byId = questions.stream().collect(Collectors.toMap(QuizQuestion::getId, q -> q));
		List<PublicQuizQuestionResponse> ordered = challenge.questionIdList().stream()
				.map(byId::get)
				.filter(Objects::nonNull)
				.map(PublicQuizQuestionResponse::from)
				.toList();
		List<UUID> myAnswered = quizAnswerRepository
				.findAllByChallengeIdAndStudentId(challengeId, principal.getOwnerId())
				.stream().map(QuizAnswer::getQuestionId).toList();

		return new ChallengeDetailResponse(toSummary(challenge, principal), ordered, myAnswered);
	}

	@Transactional
	public SubmitAnswerResponse submitAnswer(AuthPrincipal principal, UUID challengeId, SubmitAnswerRequest request) {
		requireStudent(principal);
		QuizChallenge challenge = requireParticipant(principal, challengeId);
		if (challenge.getStatus() != ChallengeStatus.ACTIVE) {
			throw new IllegalStateException("This challenge is no longer active");
		}
		UUID studentId = principal.getOwnerId();
		if (quizAnswerRepository.existsByChallengeIdAndStudentIdAndQuestionId(challengeId, studentId, request.getQuestionId())) {
			throw new IllegalStateException("You already answered this question");
		}
		if (!challenge.questionIdList().contains(request.getQuestionId())) {
			throw new IllegalArgumentException("That question isn't part of this challenge");
		}

		QuizQuestion question = quizQuestionRepository.findById(request.getQuestionId())
				.orElseThrow(() -> new EntityNotFoundException("Question not found"));
		boolean correct = question.getCorrectOption() == request.getSelectedOption();

		QuizAnswer answer = new QuizAnswer();
		answer.setSchoolId(principal.getSchoolId());
		answer.setChallengeId(challengeId);
		answer.setStudentId(studentId);
		answer.setQuestionId(request.getQuestionId());
		answer.setSelectedOption(request.getSelectedOption());
		answer.setCorrect(correct);
		quizAnswerRepository.save(answer);

		boolean completed = tryResolve(challenge);
		return new SubmitAnswerResponse(correct, completed);
	}

	/** Every night: expire challenges nobody finished within 48 hours. */
	@Scheduled(cron = "0 0 3 * * *", zone = "Asia/Kolkata")
	@Transactional
	public void expireStaleChallenges() {
		Instant cutoff = Instant.now().minusSeconds(48L * 3600);
		quizChallengeRepository.findAllByStatusAndCreatedAtBefore(ChallengeStatus.ACTIVE, cutoff)
				.forEach(c -> {
					c.setStatus(ChallengeStatus.EXPIRED);
					quizChallengeRepository.save(c);
				});
	}

	/** Resolves the challenge once both sides have answered every question. Returns true if it just resolved. */
	private boolean tryResolve(QuizChallenge challenge) {
		int total = challenge.questionIdList().size();
		int challengerAnswered = quizAnswerRepository
				.findAllByChallengeIdAndStudentId(challenge.getId(), challenge.getChallengerStudentId()).size();
		int opponentAnswered = quizAnswerRepository
				.findAllByChallengeIdAndStudentId(challenge.getId(), challenge.getOpponentStudentId()).size();
		if (challengerAnswered < total || opponentAnswered < total) {
			return false;
		}

		long challengerCorrect = countCorrect(challenge.getId(), challenge.getChallengerStudentId());
		long opponentCorrect = countCorrect(challenge.getId(), challenge.getOpponentStudentId());

		challenge.setStatus(ChallengeStatus.COMPLETED);
		if (challengerCorrect > opponentCorrect) {
			challenge.setWinnerStudentId(challenge.getChallengerStudentId());
		} else if (opponentCorrect > challengerCorrect) {
			challenge.setWinnerStudentId(challenge.getOpponentStudentId());
		}
		quizChallengeRepository.save(challenge);

		if (challenge.getWinnerStudentId() != null) {
			gamificationService.awardXp(challenge.getSchoolId(), challenge.getWinnerStudentId(), XpSource.QUIZ_WIN, WIN_XP);
		}
		return true;
	}

	private long countCorrect(UUID challengeId, UUID studentId) {
		return quizAnswerRepository.findAllByChallengeIdAndStudentId(challengeId, studentId).stream()
				.filter(QuizAnswer::isCorrect)
				.count();
	}

	private ChallengeSummaryResponse toSummary(QuizChallenge challenge, AuthPrincipal principal) {
		UUID me = principal.getOwnerId();
		boolean isChallenger = challenge.getChallengerStudentId().equals(me);
		UUID otherId = isChallenger ? challenge.getOpponentStudentId() : challenge.getChallengerStudentId();
		String opponentName = studentRepository.findByIdAndSchoolId(otherId, principal.getSchoolId())
				.map(Student::getName).orElse("Unknown");

		int total = challenge.questionIdList().size();
		int myAnswered = quizAnswerRepository.findAllByChallengeIdAndStudentId(challenge.getId(), me).size();
		int opponentAnswered = quizAnswerRepository.findAllByChallengeIdAndStudentId(challenge.getId(), otherId).size();

		Boolean youWon = null;
		boolean draw = false;
		if (challenge.getStatus() == ChallengeStatus.COMPLETED) {
			if (challenge.getWinnerStudentId() == null) {
				draw = true;
			} else {
				youWon = challenge.getWinnerStudentId().equals(me);
			}
		}

		return new ChallengeSummaryResponse(
				challenge.getId(), challenge.getSubject().getName(), opponentName, challenge.getStatus(),
				total, myAnswered, opponentAnswered, youWon, draw);
	}

	private QuizChallenge requireParticipant(AuthPrincipal principal, UUID challengeId) {
		QuizChallenge challenge = quizChallengeRepository.findByIdAndSchoolId(challengeId, principal.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Challenge not found"));
		UUID me = principal.getOwnerId();
		if (!challenge.getChallengerStudentId().equals(me) && !challenge.getOpponentStudentId().equals(me)) {
			throw new AccessDeniedException("You are not part of this challenge");
		}
		return challenge;
	}

	private void requireStudent(AuthPrincipal principal) {
		if (principal.getOwnerType() != OwnerType.STUDENT) {
			throw new AccessDeniedException("Only a student account has quiz challenges");
		}
	}

}
