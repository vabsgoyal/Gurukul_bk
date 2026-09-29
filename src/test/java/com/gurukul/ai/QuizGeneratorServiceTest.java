package com.gurukul.ai;

import com.gurukul.academics.entity.SectionSubjectTeacher;
import com.gurukul.academics.entity.Subject;
import com.gurukul.academics.repository.SectionSubjectTeacherRepository;
import com.gurukul.academics.repository.SubjectRepository;
import com.gurukul.ai.config.OpenRouterProperties;
import com.gurukul.ai.config.QuizGenProperties;
import com.gurukul.ai.dto.QuizGeneratorDtos.AiQuizGenerationRequest;
import com.gurukul.ai.dto.QuizGeneratorDtos.AiQuizGenerationResponse;
import com.gurukul.ai.dto.QuizGeneratorDtos.GeneratedQuestionType;
import com.gurukul.ai.dto.QuizGeneratorDtos.QuizDifficulty;
import com.gurukul.ai.dto.QuizGeneratorDtos.TeacherAssessmentType;
import com.gurukul.ai.provider.AiProvider;
import com.gurukul.ai.service.AiRateLimiter;
import com.gurukul.ai.service.AiUnavailableException;
import com.gurukul.ai.service.QuizGeneratorService;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.students.entity.ClassSection;
import com.gurukul.students.repository.ClassSectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Access rules, retry behaviour and wiring of the AI quiz generator, with every collaborator
 * mocked - the provider in particular, so no test ever makes a paid model call. Output validation
 * itself is covered in depth by GeneratedQuizParserTest.
 */
class QuizGeneratorServiceTest {

	private static final UUID SCHOOL = UUID.randomUUID();

	private final AiProvider provider = mock(AiProvider.class);
	private final AiRateLimiter rateLimiter = mock(AiRateLimiter.class);
	private final ClassSectionRepository sections = mock(ClassSectionRepository.class);
	private final SubjectRepository subjects = mock(SubjectRepository.class);
	private final SectionSubjectTeacherRepository assignments = mock(SectionSubjectTeacherRepository.class);
	private final EmployeeRepository employees = mock(EmployeeRepository.class);
	private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-01T10:00:00Z"));

	private ClassSection section;
	private Subject subject;
	private Employee teacher;
	private QuizGeneratorService service;

	@BeforeEach
	void setUp() {
		section = new ClassSection();
		section.setId(UUID.randomUUID());
		section.setSchoolId(SCHOOL);
		section.setClassName("Grade 8");
		section.setSection("A");
		section.setAcademicYear("2026-27");
		subject = new Subject();
		subject.setId(UUID.randomUUID());
		subject.setSchoolId(SCHOOL);
		subject.setName("Mathematics");
		teacher = new Employee();
		teacher.setId(UUID.randomUUID());
		teacher.setSchoolId(SCHOOL);
		teacher.setName("Asha Teacher");

		when(sections.findByIdAndSchoolId(section.getId(), SCHOOL)).thenReturn(Optional.of(section));
		when(subjects.findByIdAndSchoolId(subject.getId(), SCHOOL)).thenReturn(Optional.of(subject));
		when(employees.findByIdAndSchoolId(teacher.getId(), SCHOOL)).thenReturn(Optional.of(teacher));
		when(provider.isConfigured()).thenReturn(true);
		when(provider.modelId()).thenReturn("test/model");

		Clock clock = new Clock() {
			@Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
			@Override public Clock withZone(java.time.ZoneId zone) { return this; }
			@Override public Instant instant() { return now.get(); }
		};
		OpenRouterProperties openRouter = new OpenRouterProperties(
				"https://example.test/v1", "test-key", "test/model", 1500, 0.4, 60, "https://example.test", "Test", 20, 40, 60000);
		service = new QuizGeneratorService(provider, rateLimiter, openRouter, new QuizGenProperties(4000, 30),
				sections, subjects, assignments, employees, clock);
	}

	private void assignTeacher() {
		when(assignments.findBySectionIdAndSubjectIdAndTeacherId(section.getId(), subject.getId(), teacher.getId()))
				.thenReturn(Optional.of(new SectionSubjectTeacher()));
	}

	private AuthPrincipal principal(UUID ownerId, Role role) {
		OwnerType type = role == Role.STUDENT ? OwnerType.STUDENT : role == Role.PARENT ? OwnerType.PARENT : OwnerType.EMPLOYEE;
		return new AuthPrincipal(ownerId, type, role, SCHOOL, "u");
	}

	private AiQuizGenerationRequest request(int count, int marks) {
		AiQuizGenerationRequest r = new AiQuizGenerationRequest();
		r.setClassSectionId(section.getId());
		r.setSubjectId(subject.getId());
		r.setSubjectName("ignored when subjectId is set");
		r.setAssessmentType(TeacherAssessmentType.QUIZ);
		r.setTitle("Fractions quiz");
		r.setSyllabus("Chapter 2: Fractions and decimals");
		r.setDifficulty(QuizDifficulty.MEDIUM);
		r.setQuestionCount(count);
		r.setMaxMarks(marks);
		return r;
	}

	private void modelReplies(String first, String... rest) {
		when(provider.complete(anyString(), anyList(), anyInt())).thenReturn(first, rest);
	}

	@Test
	void teacherAssignedToTheSectionAndSubjectGetsAValidatedDraft() {
		assignTeacher();
		modelReplies(GeneratedQuizParserTest.VALID);

		AiQuizGenerationResponse response = service.generate(principal(teacher.getId(), Role.TEACHER), teacher.getId(), request(3, 5));

		assertThat(response.getQuestions()).hasSize(3);
		assertThat(response.getSubjectName()).isEqualTo("Mathematics");
		assertThat(response.getClassName()).isEqualTo("Grade 8");
		assertThat(response.getTeacherName()).isEqualTo("Asha Teacher");
		assertThat(response.getGeneratorMode()).isEqualTo("AI");
		assertThat(response.getReviewNote()).contains("nothing has been saved");
		assertThat(response.getModel()).isEqualTo("test/model");
		verify(rateLimiter).checkAndRecord(teacher.getId());
		verify(provider).complete(anyString(), anyList(), eq(4000));
	}

	@Test
	void teacherCannotGenerateAsAnotherTeacher() {
		UUID someoneElse = UUID.randomUUID();
		assertThatThrownBy(() -> service.generate(principal(someoneElse, Role.TEACHER), teacher.getId(), request(3, 5)))
				.isInstanceOf(AccessDeniedException.class);
		verify(provider, never()).complete(anyString(), anyList(), anyInt());
	}

	@Test
	void teacherNotAssignedToTheSubjectIsRejected() {
		when(assignments.findBySectionIdAndSubjectIdAndTeacherId(any(), any(), any())).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.generate(principal(teacher.getId(), Role.TEACHER), teacher.getId(), request(3, 5)))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("class and subject you teach");
		verify(rateLimiter, never()).checkAndRecord(any());
	}

	@Test
	void teacherMustNameTheSubject() {
		AiQuizGenerationRequest r = request(3, 5);
		r.setSubjectId(null);
		assertThatThrownBy(() -> service.generate(principal(teacher.getId(), Role.TEACHER), teacher.getId(), r))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("subjectId");
	}

	@Test
	void adminMayGenerateForAnySectionOfTheSchoolWithoutAnAssignment() {
		modelReplies(GeneratedQuizParserTest.VALID);
		AiQuizGenerationRequest r = request(3, 5);
		r.setSubjectId(null);
		r.setSubjectName(" General Science ");

		AiQuizGenerationResponse response = service.generate(principal(UUID.randomUUID(), Role.ADMIN), teacher.getId(), r);

		assertThat(response.getSubjectName()).isEqualTo("General Science");
		assertThat(response.getSubjectId()).isNull();
	}

	@Test
	void sectionFromAnotherSchoolIsNotFound() {
		AiQuizGenerationRequest r = request(3, 5);
		r.setClassSectionId(UUID.randomUUID());
		assertThatThrownBy(() -> service.generate(principal(UUID.randomUUID(), Role.ADMIN), teacher.getId(), r))
				.isInstanceOf(EntityNotFoundException.class);
	}

	@Test
	void studentsAndParentsAreRejected() {
		assertThatThrownBy(() -> service.generate(principal(UUID.randomUUID(), Role.STUDENT), teacher.getId(), request(3, 5)))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> service.generate(principal(UUID.randomUUID(), Role.PARENT), teacher.getId(), request(3, 5)))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void requestLimitsAreEnforcedBeforeCallingTheModel() {
		AuthPrincipal admin = principal(UUID.randomUUID(), Role.ADMIN);
		assertThatThrownBy(() -> service.generate(admin, teacher.getId(), request(31, 40)))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at most 30");
		assertThatThrownBy(() -> service.generate(admin, teacher.getId(), request(10, 5)))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxMarks");
		verify(provider, never()).complete(anyString(), anyList(), anyInt());
	}

	@Test
	void unconfiguredProviderIsAReadable503() {
		when(provider.isConfigured()).thenReturn(false);
		assertThatThrownBy(() -> service.generate(principal(UUID.randomUUID(), Role.ADMIN), teacher.getId(), request(3, 5)))
				.isInstanceOf(AiUnavailableException.class)
				.hasMessageContaining("isn't set up");
	}

	@Test
	void retriesOnceWithTheReasonWhenTheFirstReplyIsInvalid() {
		modelReplies("not json at all", GeneratedQuizParserTest.VALID);

		AiQuizGenerationResponse response = service.generate(principal(UUID.randomUUID(), Role.ADMIN), teacher.getId(), request(3, 5));

		assertThat(response.getQuestions()).hasSize(3);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<AiProvider.ChatTurn>> turns = ArgumentCaptor.forClass(List.class);
		verify(provider, times(2)).complete(anyString(), turns.capture(), anyInt());
		List<AiProvider.ChatTurn> retryTurns = turns.getAllValues().get(1);
		assertThat(retryTurns).hasSize(3);
		assertThat(retryTurns.get(1).role()).isEqualTo("assistant");
		assertThat(retryTurns.get(2).content()).contains("not JSON");
		verify(rateLimiter, times(1)).checkAndRecord(any());
	}

	@Test
	void givesUpAfterTheSecondInvalidReply() {
		modelReplies("nope", "still nope");
		assertThatThrownBy(() -> service.generate(principal(UUID.randomUUID(), Role.ADMIN), teacher.getId(), request(3, 5)))
				.isInstanceOf(AiUnavailableException.class)
				.hasMessageContaining("couldn't produce a valid quiz");
		verify(provider, times(2)).complete(anyString(), anyList(), anyInt());
	}

	@Test
	void doesNotRetryWhenTheFirstAttemptWasAlreadySlow() {
		when(provider.complete(anyString(), anyList(), anyInt())).thenAnswer(invocation -> {
			now.set(now.get().plus(Duration.ofSeconds(35))); // over half of the 60s timeout
			return "nope";
		});
		assertThatThrownBy(() -> service.generate(principal(UUID.randomUUID(), Role.ADMIN), teacher.getId(), request(3, 5)))
				.isInstanceOf(AiUnavailableException.class);
		verify(provider, times(1)).complete(anyString(), anyList(), anyInt());
	}

	@Test
	void promptCarriesTheRequestedTypesAndTotals() {
		modelReplies(GeneratedQuizParserTest.VALID);
		AiQuizGenerationRequest r = request(3, 5);
		r.setQuestionTypes(List.of(GeneratedQuestionType.MCQ, GeneratedQuestionType.NUMERIC, GeneratedQuestionType.SHORT_WORD));

		service.generate(principal(UUID.randomUUID(), Role.ADMIN), teacher.getId(), r);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<AiProvider.ChatTurn>> turns = ArgumentCaptor.forClass(List.class);
		verify(provider).complete(anyString(), turns.capture(), anyInt());
		String prompt = turns.getValue().get(0).content();
		assertThat(prompt).contains("Grade: Grade 8", "Subject: Mathematics", "exactly 3", "Total marks: exactly 5",
				"[MCQ, NUMERIC, SHORT_WORD]");
	}

}
