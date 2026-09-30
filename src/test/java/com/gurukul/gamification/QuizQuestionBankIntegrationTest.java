package com.gurukul.gamification;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.gamification.dto.ArenaDtos.BankQuestionInput;
import com.gurukul.gamification.dto.ArenaDtos.BulkCreateQuizQuestionsRequest;
import com.gurukul.gamification.dto.ArenaDtos.ChallengeDetailResponse;
import com.gurukul.gamification.dto.ArenaDtos.ChallengeSummaryResponse;
import com.gurukul.gamification.dto.ArenaDtos.CreateChallengeRequest;
import com.gurukul.gamification.dto.ArenaDtos.PublicQuizQuestionResponse;
import com.gurukul.gamification.dto.ArenaDtos.QuizQuestionResponse;
import com.gurukul.gamification.dto.PracticeDtos.CreatePracticeSessionRequest;
import com.gurukul.gamification.dto.PracticeDtos.PracticeSessionResponse;
import com.gurukul.gamification.entity.QuizOption;
import com.gurukul.gamification.entity.QuizQuestionType;
import com.gurukul.gamification.repository.QuizQuestionRepository;
import com.gurukul.gamification.service.ArenaService;
import com.gurukul.gamification.service.PracticeService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The question bank's new NUMERIC / SHORT_WORD types: the bulk save used after reviewing an AI
 * quiz (validation, all-or-nothing, who may save where), and the guarantee that Arena games keep
 * drawing MCQ questions only, however mixed the bank gets.
 */
@SpringBootTest
@AutoConfigureMockMvc
class QuizQuestionBankIntegrationTest {

	private static final UUID SCHOOL_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	/** Seeded Grade 8 - A */
	private static final String CLASS_SECTION_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
	private static final String GRADE = "Grade 8";

	@Autowired private MockMvc mockMvc;
	@Autowired private ArenaService arenaService;
	@Autowired private PracticeService practiceService;
	@Autowired private QuizQuestionRepository quizQuestionRepository;

	private UUID subjectId;
	private AuthPrincipal teacher;
	private AuthPrincipal otherTeacher;

	@BeforeEach
	void setUp() throws Exception {
		subjectId = UUID.fromString(post("/api/v1/subjects", """
				{"code": "QB-%s", "name": "Bank Science"}
				""".formatted(UUID.randomUUID().toString().substring(0, 8))));
		teacher = employee("Bank Teacher", Role.TEACHER);
		otherTeacher = employee("Other Teacher", Role.TEACHER);
		postExpectingOk("/api/v1/class-sections/" + CLASS_SECTION_A + "/subjects", """
				{"subjectId": "%s", "teacherId": "%s"}
				""".formatted(subjectId, teacher.getOwnerId()));
	}

	@Test
	void teacherSavesAReviewedMixOfTypes() {
		List<QuizQuestionResponse> saved = arenaService.bulkCreateQuestions(teacher, bulk(GRADE,
				mcq("2 + 2 = ?", QuizOption.B), numeric("Half of 5?", " 2.5 "), shortWord("Capital of India?", " New   Delhi ")));

		assertThat(saved).hasSize(3);
		assertThat(saved).extracting(QuizQuestionResponse::getQuestionType)
				.containsExactly(QuizQuestionType.MCQ, QuizQuestionType.NUMERIC, QuizQuestionType.SHORT_WORD);
		assertThat(saved.get(0).getCorrectOption()).isEqualTo(QuizOption.B);
		assertThat(saved.get(1).getAnswerText()).isEqualTo("2.5");
		assertThat(saved.get(1).getOptionA()).isNull();
		assertThat(saved.get(2).getAnswerText()).isEqualTo("New Delhi");
		assertThat(saved).allSatisfy(q -> {
			assertThat(q.getClassName()).isEqualTo(GRADE);
			assertThat(q.getCreatedByEmployeeId()).isEqualTo(teacher.getOwnerId());
		});

		assertThat(arenaService.listQuestions(teacher, subjectId, GRADE, null)).hasSize(3);
	}

	@Test
	void oneBadQuestionSavesNothingAndNamesTheQuestion() {
		assertThatThrownBy(() -> arenaService.bulkCreateQuestions(teacher, bulk(GRADE,
				mcq("2 + 2 = ?", QuizOption.B), shortWord("Gas plants absorb?", "carbon dioxide"), numeric("How many legs?", "four"))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Question 3")
				.hasMessageContaining("number");
		assertThat(quizQuestionRepository.findAllBySchoolIdAndSubjectId(SCHOOL_ID, subjectId)).isEmpty();
	}

	@Test
	void perTypeRulesAreEnforced() {
		BankQuestionInput threeOptions = mcq("Pick one", QuizOption.A);
		threeOptions.setOptionD(" ");
		assertThatThrownBy(() -> arenaService.bulkCreateQuestions(teacher, bulk(GRADE, threeOptions)))
				.hasMessageContaining("all four options");

		BankQuestionInput duplicateOptions = mcq("Pick one", QuizOption.A);
		duplicateOptions.setOptionB(duplicateOptions.getOptionA().toUpperCase());
		assertThatThrownBy(() -> arenaService.bulkCreateQuestions(teacher, bulk(GRADE, duplicateOptions)))
				.hasMessageContaining("different");

		BankQuestionInput noCorrect = mcq("Pick one", null);
		assertThatThrownBy(() -> arenaService.bulkCreateQuestions(teacher, bulk(GRADE, noCorrect)))
				.hasMessageContaining("correct option");

		assertThatThrownBy(() -> arenaService.bulkCreateQuestions(teacher, bulk(GRADE, shortWord("Describe it", "a long three words"))))
				.hasMessageContaining("one- or two-word");
	}

	@Test
	void teacherCanOnlySaveIntoASubjectAndGradeTheyTeach() {
		assertThatThrownBy(() -> arenaService.bulkCreateQuestions(otherTeacher, bulk(GRADE, mcq("Q", QuizOption.A))))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> arenaService.bulkCreateQuestions(teacher, bulk("Grade 9", mcq("Q", QuizOption.A))))
				.isInstanceOf(AccessDeniedException.class);

		AuthPrincipal admin = new AuthPrincipal(otherTeacher.getOwnerId(), OwnerType.EMPLOYEE, Role.ADMIN, SCHOOL_ID, "admin");
		assertThat(arenaService.bulkCreateQuestions(admin, bulk("Grade 9", mcq("Q", QuizOption.A)))).hasSize(1);

		AuthPrincipal student = new AuthPrincipal(UUID.randomUUID(), OwnerType.STUDENT, Role.STUDENT, SCHOOL_ID, "s");
		assertThatThrownBy(() -> arenaService.bulkCreateQuestions(student, bulk(GRADE, mcq("Q", QuizOption.A))))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void arenaChallengesAndPracticeOnlyEverUseMcqQuestions() throws Exception {
		List<BankQuestionInput> mixed = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			mixed.add(mcq("MCQ " + i, QuizOption.A));
			mixed.add(numeric("Numeric " + i, String.valueOf(i)));
			mixed.add(shortWord("Word " + i, "word" + i));
		}
		List<QuizQuestionResponse> saved = arenaService.bulkCreateQuestions(teacher, bulk(GRADE, mixed.toArray(BankQuestionInput[]::new)));
		Set<UUID> mcqIds = saved.stream().filter(q -> q.getQuestionType() == QuizQuestionType.MCQ)
				.map(QuizQuestionResponse::getId).collect(Collectors.toSet());

		AuthPrincipal alice = student("Alice Bank");
		AuthPrincipal bob = student("Bob Bank");
		CreateChallengeRequest create = new CreateChallengeRequest();
		create.setOpponentStudentId(bob.getOwnerId());
		create.setSubjectId(subjectId);
		ChallengeSummaryResponse challenge = arenaService.createChallenge(alice, create);
		ChallengeDetailResponse detail = arenaService.getChallenge(alice, challenge.getId());
		assertThat(detail.getQuestions()).hasSize(5)
				.extracting(PublicQuizQuestionResponse::getId).allMatch(mcqIds::contains);
		assertThat(detail.getQuestions()).allSatisfy(q -> assertThat(q.getOptionA()).isNotBlank());

		CreatePracticeSessionRequest practice = new CreatePracticeSessionRequest();
		practice.setSubjectId(subjectId);
		PracticeSessionResponse session = practiceService.createSession(alice, practice);
		assertThat(session.getQuestions()).hasSize(5)
				.extracting(PublicQuizQuestionResponse::getId).allMatch(mcqIds::contains);
	}

	@Test
	void nonMcqQuestionsDoNotCountTowardsTheChallengeMinimum() throws Exception {
		List<BankQuestionInput> mostlyTyped = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			mostlyTyped.add(mcq("MCQ " + i, QuizOption.A));
		}
		for (int i = 0; i < 6; i++) {
			mostlyTyped.add(numeric("Numeric " + i, String.valueOf(i)));
		}
		arenaService.bulkCreateQuestions(teacher, bulk(GRADE, mostlyTyped.toArray(BankQuestionInput[]::new)));

		AuthPrincipal alice = student("Alice Few");
		AuthPrincipal bob = student("Bob Few");
		CreateChallengeRequest create = new CreateChallengeRequest();
		create.setOpponentStudentId(bob.getOwnerId());
		create.setSubjectId(subjectId);
		assertThatThrownBy(() -> arenaService.createChallenge(alice, create))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Not enough");
	}

	@Test
	void bothNewEndpointsRequireAStaffLogin() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/gamification/arena/questions/bulk")
						.header("X-School-Id", SCHOOL_ID.toString())
						.header("Authorization", "")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/teachers/" + teacher.getOwnerId() + "/ai/quiz-generator")
						.header("X-School-Id", SCHOOL_ID.toString())
						.header("Authorization", "")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isUnauthorized());
	}

	private BulkCreateQuizQuestionsRequest bulk(String className, BankQuestionInput... questions) {
		BulkCreateQuizQuestionsRequest request = new BulkCreateQuizQuestionsRequest();
		request.setSubjectId(subjectId);
		request.setClassName(className);
		request.setQuestions(List.of(questions));
		return request;
	}

	private static BankQuestionInput mcq(String text, QuizOption correct) {
		BankQuestionInput q = new BankQuestionInput();
		q.setQuestionType(QuizQuestionType.MCQ);
		q.setQuestionText(text);
		q.setOptionA(text + " option one");
		q.setOptionB(text + " option two");
		q.setOptionC(text + " option three");
		q.setOptionD(text + " option four");
		q.setCorrectOption(correct);
		return q;
	}

	private static BankQuestionInput numeric(String text, String answer) {
		BankQuestionInput q = new BankQuestionInput();
		q.setQuestionType(QuizQuestionType.NUMERIC);
		q.setQuestionText(text);
		q.setAnswerText(answer);
		return q;
	}

	private static BankQuestionInput shortWord(String text, String answer) {
		BankQuestionInput q = new BankQuestionInput();
		q.setQuestionType(QuizQuestionType.SHORT_WORD);
		q.setQuestionText(text);
		q.setAnswerText(answer);
		return q;
	}

	private AuthPrincipal employee(String name, Role role) throws Exception {
		UUID id = UUID.fromString(post("/api/v1/employees", """
				{"name": "%s", "designation": "Teacher", "joinDate": "2024-04-01"}
				""".formatted(name)));
		return new AuthPrincipal(id, OwnerType.EMPLOYEE, role, SCHOOL_ID, name);
	}

	private AuthPrincipal student(String name) throws Exception {
		UUID studentId = UUID.fromString(post("/api/v1/students", """
				{
				  "name": "%s",
				  "dob": "2012-05-15",
				  "gender": "MALE",
				  "address": "1 Test Road",
				  "parentName": "Parent",
				  "parentContact": "9876543210",
				  "classSectionId": "%s",
				  "admissionDate": "2026-04-01"
				}
				""".formatted(name, CLASS_SECTION_A)));
		return new AuthPrincipal(studentId, OwnerType.STUDENT, Role.STUDENT, SCHOOL_ID, name);
	}

	private String post(String url, String body) throws Exception {
		return JsonPath.read(postExpectingOk(url, body), "$.data.id");
	}

	private String postExpectingOk(String url, String body) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.post(url)
						.header("X-School-Id", SCHOOL_ID.toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

}
