package com.gurukul.gamification.service;

import com.gurukul.academics.entity.Subject;
import com.gurukul.academics.repository.SubjectRepository;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.gamification.dto.ArenaDtos.PublicQuizQuestionResponse;
import com.gurukul.gamification.dto.PracticeDtos.CreatePracticeSessionRequest;
import com.gurukul.gamification.dto.PracticeDtos.PracticeSessionResponse;
import com.gurukul.gamification.dto.PracticeDtos.SubmitPracticeAnswerRequest;
import com.gurukul.gamification.dto.PracticeDtos.SubmitPracticeAnswerResponse;
import com.gurukul.gamification.entity.PracticeAnswer;
import com.gurukul.gamification.entity.PracticeSession;
import com.gurukul.gamification.entity.PracticeSessionStatus;
import com.gurukul.gamification.entity.QuizQuestion;
import com.gurukul.gamification.repository.PracticeAnswerRepository;
import com.gurukul.gamification.repository.PracticeSessionRepository;
import com.gurukul.gamification.repository.QuizQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Solo, no-stakes practice: a student picks a subject and works through a fixed question set
 * (same quiz_question bank Arena/Battle Rooms use) at their own pace. Deliberately not part of
 * the XP economy - see PracticeSession's class doc.
 */
@Service
@RequiredArgsConstructor
public class PracticeService {

	private static final int QUESTIONS_PER_SESSION = 10;

	private final PracticeSessionRepository sessionRepository;
	private final PracticeAnswerRepository answerRepository;
	private final QuizQuestionRepository quizQuestionRepository;
	private final SubjectRepository subjectRepository;

	@Transactional
	public PracticeSessionResponse createSession(AuthPrincipal principal, CreatePracticeSessionRequest request) {
		requireStudent(principal);
		UUID schoolId = principal.getSchoolId();
		Subject subject = subjectRepository.findByIdAndSchoolId(request.getSubjectId(), schoolId)
				.orElseThrow(() -> new EntityNotFoundException("Subject not found"));

		List<QuizQuestion> pool = new ArrayList<>(quizQuestionRepository.findAllBySchoolIdAndSubjectId(schoolId, subject.getId()));
		if (pool.isEmpty()) {
			throw new IllegalStateException("No " + subject.getName() + " questions available yet");
		}
		Collections.shuffle(pool, new SecureRandom());
		List<QuizQuestion> picked = pool.subList(0, Math.min(QUESTIONS_PER_SESSION, pool.size()));

		PracticeSession session = new PracticeSession();
		session.setSchoolId(schoolId);
		session.setStudentId(principal.getOwnerId());
		session.setSubject(subject);
		session.setQuestionIds(PracticeSession.joinQuestionIds(picked.stream().map(QuizQuestion::getId).toList()));
		session.setStatus(PracticeSessionStatus.ACTIVE);
		session = sessionRepository.save(session);

		return buildResponse(session);
	}

	@Transactional(readOnly = true)
	public PracticeSessionResponse getSession(AuthPrincipal principal, UUID sessionId) {
		requireStudent(principal);
		return buildResponse(requireOwnSession(principal, sessionId));
	}

	@Transactional
	public SubmitPracticeAnswerResponse submitAnswer(AuthPrincipal principal, UUID sessionId, SubmitPracticeAnswerRequest request) {
		requireStudent(principal);
		PracticeSession session = requireOwnSession(principal, sessionId);
		if (session.getStatus() != PracticeSessionStatus.ACTIVE) {
			throw new IllegalStateException("This practice session is already finished");
		}
		if (!session.questionIdList().contains(request.getQuestionId())) {
			throw new IllegalArgumentException("That question isn't part of this session");
		}
		if (answerRepository.existsBySessionIdAndQuestionId(sessionId, request.getQuestionId())) {
			throw new IllegalStateException("You already answered this question");
		}

		QuizQuestion question = quizQuestionRepository.findById(request.getQuestionId())
				.orElseThrow(() -> new EntityNotFoundException("Question not found"));
		boolean correct = question.getCorrectOption() == request.getSelectedOption();

		PracticeAnswer answer = new PracticeAnswer();
		answer.setSchoolId(principal.getSchoolId());
		answer.setSessionId(sessionId);
		answer.setQuestionId(request.getQuestionId());
		answer.setSelectedOption(request.getSelectedOption());
		answer.setCorrect(correct);
		answerRepository.save(answer);

		int answeredCount = answerRepository.findAllBySessionId(sessionId).size();
		boolean completed = answeredCount >= session.questionIdList().size();
		if (completed) {
			session.setStatus(PracticeSessionStatus.COMPLETED);
			sessionRepository.save(session);
		}

		return new SubmitPracticeAnswerResponse(correct, completed);
	}

	private PracticeSessionResponse buildResponse(PracticeSession session) {
		List<QuizQuestion> questions = quizQuestionRepository.findAllById(session.questionIdList());
		Map<UUID, QuizQuestion> byId = questions.stream().collect(Collectors.toMap(QuizQuestion::getId, q -> q));
		List<PublicQuizQuestionResponse> ordered = session.questionIdList().stream()
				.map(byId::get)
				.filter(Objects::nonNull)
				.map(PublicQuizQuestionResponse::from)
				.toList();

		List<PracticeAnswer> answers = answerRepository.findAllBySessionId(session.getId());
		List<UUID> answeredIds = answers.stream().map(PracticeAnswer::getQuestionId).toList();
		long correctCount = answers.stream().filter(PracticeAnswer::isCorrect).count();

		return new PracticeSessionResponse(
				session.getId(), session.getSubject().getName(), session.getStatus(),
				session.questionIdList().size(), answers.size(), (int) correctCount, ordered, answeredIds);
	}

	private PracticeSession requireOwnSession(AuthPrincipal principal, UUID sessionId) {
		return sessionRepository.findByIdAndSchoolIdAndStudentId(sessionId, principal.getSchoolId(), principal.getOwnerId())
				.orElseThrow(() -> new EntityNotFoundException("Practice session not found"));
	}

	private void requireStudent(AuthPrincipal principal) {
		if (principal.getOwnerType() != OwnerType.STUDENT) {
			throw new AccessDeniedException("Only a student account has practice sessions");
		}
	}

}
