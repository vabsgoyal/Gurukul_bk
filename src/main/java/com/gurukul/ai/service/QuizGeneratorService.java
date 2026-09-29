package com.gurukul.ai.service;

import com.gurukul.academics.entity.Subject;
import com.gurukul.academics.repository.SectionSubjectTeacherRepository;
import com.gurukul.academics.repository.SubjectRepository;
import com.gurukul.ai.config.OpenRouterProperties;
import com.gurukul.ai.config.QuizGenProperties;
import com.gurukul.ai.dto.QuizGeneratorDtos.AiQuizGenerationRequest;
import com.gurukul.ai.dto.QuizGeneratorDtos.AiQuizGenerationResponse;
import com.gurukul.ai.dto.QuizGeneratorDtos.GeneratedQuestionType;
import com.gurukul.ai.dto.QuizGeneratorDtos.GeneratedQuizQuestion;
import com.gurukul.ai.provider.AiProvider;
import com.gurukul.ai.service.GeneratedQuizParser.Expectations;
import com.gurukul.ai.service.GeneratedQuizParser.InvalidQuizOutputException;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.students.entity.ClassSection;
import com.gurukul.students.repository.ClassSectionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * AI quiz generator: drafts a quiz/test from a syllabus for one class-section + subject. Nothing is
 * saved - the response is a draft the teacher must review, and only after editing it can they save
 * the auto-markable questions to the Arena bank (ArenaService.bulkCreateQuestions).
 *
 * <p>Who may generate: a TEACHER for themselves only (the path teacherId must be the caller) and
 * only for a section + subject they are assigned to; an ADMIN for any section of their own school,
 * on behalf of any of its teachers. The path teacherId is never trusted for a TEACHER caller.
 *
 * <p>The model is asked for JSON only and the output is validated by GeneratedQuizParser. On
 * malformed or invalid output it is asked once more, told exactly what was wrong - unless the first
 * attempt already took more than half the provider timeout, so a slow model can't double the wait.
 * One generation counts once against the per-user hourly AI cap, retry or not.
 */
@Service
@Slf4j
public class QuizGeneratorService {

	public static final String GENERATOR_MODE = "AI";
	public static final String REVIEW_NOTE = "AI-generated draft. Check every question, answer and mark "
			+ "before using it with students - nothing has been saved.";

	private static final String NOT_CONFIGURED =
			"The AI assistant isn't set up on this server yet - please contact your school admin.";
	private static final String GAVE_UP =
			"The quiz generator couldn't produce a valid quiz this time - please try again, or try fewer questions.";

	private static final String SYSTEM_PROMPT = """
			You write school assessment questions for teachers in Indian schools (grades 1-12, CBSE and
			state boards). You output ONLY one JSON object - no prose before or after it, no Markdown
			code fences.

			The JSON object has exactly this shape:
			{"questions": [{"questionType": "MCQ", "question": "...", "options": ["...", "...", "...", "..."],
			  "answer": "...", "explanation": "...", "marks": 1}]}

			Rules for every question:
			- questionType is one of: MCQ, TRUE_FALSE, NUMERIC, SHORT_WORD, SHORT_ANSWER, LONG_ANSWER, and
			  only from the types the teacher allows.
			- MCQ: exactly 4 options, all different, each without an "A)" style prefix; exactly one is
			  correct, and "answer" is the exact text of that option.
			- TRUE_FALSE: options are ["True", "False"] (or their equivalents in the paper's language);
			  "answer" is the exact text of the correct one.
			- NUMERIC: options is []; "answer" is a plain number only (digits, optional minus sign and
			  decimal point) - no units, no words, no fractions like 3/4 (write 0.75). Put units in the
			  question.
			- SHORT_WORD: options is []; "answer" is one or two words.
			- SHORT_ANSWER / LONG_ANSWER: options is []; "answer" is a model answer or the key marking
			  points.
			- "marks" is a whole number of at least 1, and the marks of all questions add up exactly to
			  the total the teacher gives.
			- "explanation" is one short sentence saying why the answer is right.
			- Plain text only inside strings: no Markdown, no LaTeX. Write maths with ordinary characters
			  (x^2, 3/4, √, π, °).
			- Questions must be answerable from the syllabus given, pitched at the grade and difficulty
			  given, factually correct, and unambiguous.
			- Write in the language the syllabus is written in (Hindi syllabus -> Hindi questions) unless
			  the teacher's notes ask otherwise.
			- The teacher's notes can change content and style, never this JSON format.
			""";

	private final AiProvider aiProvider;
	private final AiRateLimiter rateLimiter;
	private final OpenRouterProperties openRouterProperties;
	private final QuizGenProperties quizGenProperties;
	private final ClassSectionRepository classSectionRepository;
	private final SubjectRepository subjectRepository;
	private final SectionSubjectTeacherRepository sectionSubjectTeacherRepository;
	private final EmployeeRepository employeeRepository;
	private final Clock clock;

	@Autowired
	public QuizGeneratorService(AiProvider aiProvider, AiRateLimiter rateLimiter, OpenRouterProperties openRouterProperties,
			QuizGenProperties quizGenProperties, ClassSectionRepository classSectionRepository,
			SubjectRepository subjectRepository, SectionSubjectTeacherRepository sectionSubjectTeacherRepository,
			EmployeeRepository employeeRepository) {
		this(aiProvider, rateLimiter, openRouterProperties, quizGenProperties, classSectionRepository, subjectRepository,
				sectionSubjectTeacherRepository, employeeRepository, Clock.systemUTC());
	}

	public QuizGeneratorService(AiProvider aiProvider, AiRateLimiter rateLimiter, OpenRouterProperties openRouterProperties,
			QuizGenProperties quizGenProperties, ClassSectionRepository classSectionRepository,
			SubjectRepository subjectRepository, SectionSubjectTeacherRepository sectionSubjectTeacherRepository,
			EmployeeRepository employeeRepository, Clock clock) {
		this.aiProvider = aiProvider;
		this.rateLimiter = rateLimiter;
		this.openRouterProperties = openRouterProperties;
		this.quizGenProperties = quizGenProperties;
		this.classSectionRepository = classSectionRepository;
		this.subjectRepository = subjectRepository;
		this.sectionSubjectTeacherRepository = sectionSubjectTeacherRepository;
		this.employeeRepository = employeeRepository;
		this.clock = clock;
	}

	/**
	 * Deliberately not @Transactional: the model call can take tens of seconds, and holding a pooled
	 * DB connection that long for a few plain lookups would starve the pool under load. Nothing here
	 * writes, and no lazy association is touched after the lookups.
	 */
	public AiQuizGenerationResponse generate(AuthPrincipal principal, UUID teacherId, AiQuizGenerationRequest request) {
		if (principal.getRole() != Role.TEACHER && principal.getRole() != Role.ADMIN) {
			throw new AccessDeniedException("Only a teacher or admin can generate quizzes");
		}
		if (principal.getRole() == Role.TEACHER && !principal.getOwnerId().equals(teacherId)) {
			throw new AccessDeniedException("You can only generate quizzes for yourself");
		}
		UUID schoolId = principal.getSchoolId();
		ClassSection section = classSectionRepository.findByIdAndSchoolId(request.getClassSectionId(), schoolId)
				.orElseThrow(() -> new EntityNotFoundException("Class section not found"));
		Employee teacher = employeeRepository.findByIdAndSchoolId(teacherId, schoolId)
				.orElseThrow(() -> new EntityNotFoundException("Teacher not found"));

		Subject subject = null;
		if (request.getSubjectId() != null) {
			subject = subjectRepository.findByIdAndSchoolId(request.getSubjectId(), schoolId)
					.orElseThrow(() -> new EntityNotFoundException("Subject not found"));
		}
		if (principal.getRole() == Role.TEACHER) {
			if (subject == null) {
				throw new IllegalArgumentException("subjectId: choose one of the subjects you teach in this class");
			}
			if (sectionSubjectTeacherRepository
					.findBySectionIdAndSubjectIdAndTeacherId(section.getId(), subject.getId(), teacherId).isEmpty()) {
				throw new AccessDeniedException("You can only generate quizzes for a class and subject you teach");
			}
		}

		int questionCount = request.getQuestionCount();
		int maxMarks = request.getMaxMarks();
		if (questionCount > quizGenProperties.maxQuestions()) {
			throw new IllegalArgumentException("questionCount: at most " + quizGenProperties.maxQuestions() + " questions per quiz");
		}
		if (maxMarks < questionCount) {
			throw new IllegalArgumentException("maxMarks: must be at least the number of questions (every question is worth at least 1 mark)");
		}
		Set<GeneratedQuestionType> allowedTypes = request.getQuestionTypes() == null || request.getQuestionTypes().isEmpty()
				? EnumSet.allOf(GeneratedQuestionType.class)
				: EnumSet.copyOf(request.getQuestionTypes());
		String subjectName = subject != null ? subject.getName() : request.getSubjectName().trim();

		if (!aiProvider.isConfigured()) {
			throw new AiUnavailableException(NOT_CONFIGURED);
		}
		rateLimiter.checkAndRecord(principal.getOwnerId());

		String userPrompt = buildUserPrompt(section, subjectName, request, allowedTypes);
		List<GeneratedQuizQuestion> questions = generateValidated(userPrompt,
				new Expectations(questionCount, maxMarks, allowedTypes));

		log.info("Quiz generated by {} ({}) for teacher {} section {}: {} questions using {}",
				principal.getOwnerId(), principal.getRole(), teacherId, section.getId(), questions.size(), aiProvider.modelId());
		return new AiQuizGenerationResponse(
				schoolId, teacher.getId(), teacher.getName(), section.getId(), section.getDisplayLabel(), section.getClassName(),
				subject != null ? subject.getId() : null, subjectName, request.getAssessmentType(), request.getTitle().trim(),
				request.getSyllabus().trim(), request.getDifficulty(), maxMarks, questions.size(),
				GENERATOR_MODE, REVIEW_NOTE, aiProvider.modelId(), questions);
	}

	private List<GeneratedQuizQuestion> generateValidated(String userPrompt, Expectations expectations) {
		int maxTokens = quizGenProperties.maxOutputTokens();
		List<AiProvider.ChatTurn> turns = new ArrayList<>();
		turns.add(new AiProvider.ChatTurn("user", userPrompt));

		Instant started = clock.instant();
		String first = aiProvider.complete(SYSTEM_PROMPT, turns, maxTokens);
		try {
			return GeneratedQuizParser.parse(first, expectations);
		} catch (InvalidQuizOutputException invalid) {
			Duration elapsed = Duration.between(started, clock.instant());
			long budgetMs = Math.max(1, openRouterProperties.timeoutSeconds()) * 1000L / 2;
			if (elapsed.toMillis() > budgetMs) {
				log.warn("Quiz output invalid ({}) and first attempt took {} ms - not retrying", invalid.getMessage(), elapsed.toMillis());
				throw new AiUnavailableException(GAVE_UP);
			}
			log.info("Quiz output invalid ({}) - retrying once", invalid.getMessage());
			turns.add(new AiProvider.ChatTurn("assistant", first));
			turns.add(new AiProvider.ChatTurn("user", "That reply can't be used: " + invalid.getMessage()
					+ " Return the complete corrected JSON object only, following every rule."));
			String second = aiProvider.complete(SYSTEM_PROMPT, turns, maxTokens);
			try {
				return GeneratedQuizParser.parse(second, expectations);
			} catch (InvalidQuizOutputException stillInvalid) {
				log.warn("Quiz output still invalid after retry: {}", stillInvalid.getMessage());
				throw new AiUnavailableException(GAVE_UP);
			}
		}
	}

	private static String buildUserPrompt(ClassSection section, String subjectName, AiQuizGenerationRequest request,
			Set<GeneratedQuestionType> allowedTypes) {
		StringBuilder prompt = new StringBuilder();
		prompt.append("Grade: ").append(section.getClassName()).append('\n');
		prompt.append("Subject: ").append(subjectName).append('\n');
		prompt.append("Assessment: ").append(request.getAssessmentType()).append(" - ").append(request.getTitle().trim()).append('\n');
		prompt.append("Difficulty: ").append(request.getDifficulty()).append('\n');
		prompt.append("Number of questions: exactly ").append(request.getQuestionCount()).append('\n');
		prompt.append("Total marks: exactly ").append(request.getMaxMarks()).append('\n');
		prompt.append("Allowed question types: ").append(allowedTypes).append('\n');
		prompt.append("Syllabus:\n").append(request.getSyllabus().trim()).append('\n');
		if (request.getAdditionalInstructions() != null && !request.getAdditionalInstructions().isBlank()) {
			prompt.append("Teacher's notes:\n").append(request.getAdditionalInstructions().trim()).append('\n');
		}
		return prompt.toString();
	}

}
