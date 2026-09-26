package com.gurukul.gamification;

import com.gurukul.academics.repository.SubjectRepository;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.gamification.dto.BattleRoomDtos.BattleQuestionOutcome;
import com.gurukul.gamification.dto.BattleRoomDtos.BattleQuestionResultResponse;
import com.gurukul.gamification.dto.BattleRoomDtos.BattleRoomResponse;
import com.gurukul.gamification.dto.BattleRoomDtos.CreateBattleRoomRequest;
import com.gurukul.gamification.dto.BattleRoomDtos.SubmitBattleAnswerRequest;
import com.gurukul.gamification.entity.BattleRoom;
import com.gurukul.gamification.entity.BattleRoomStatus;
import com.gurukul.gamification.entity.QuizOption;
import com.gurukul.gamification.entity.QuizQuestion;
import com.gurukul.gamification.repository.BattleRoomRepository;
import com.gurukul.gamification.repository.QuizQuestionRepository;
import com.gurukul.gamification.service.BattleRoomService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Battle room answer reveal: once a question closes (answered or timed out) every participant gets
 * its correct option in lastResult, and the next question only opens for buzzing after the reveal
 * pause.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BattleRoomRevealIntegrationTest {

	private static final UUID SCHOOL_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final String CLASS_SECTION_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

	@Autowired private MockMvc mockMvc;
	@Autowired private BattleRoomService battleRoomService;
	@Autowired private BattleRoomRepository battleRoomRepository;
	@Autowired private QuizQuestionRepository quizQuestionRepository;
	@Autowired private SubjectRepository subjectRepository;
	@Autowired private EmployeeRepository employeeRepository;

	private AuthPrincipal alice;
	private AuthPrincipal bob;
	private UUID subjectId;
	/** questionId -> correct option, for the questions created for this test's subject */
	private final Map<UUID, QuizOption> answerKey = new HashMap<>();

	@BeforeEach
	void setUp() throws Exception {
		alice = student("Alice Reveal");
		bob = student("Bob Reveal");
		subjectId = UUID.fromString(post("/api/v1/subjects", """
				{"code": "BR-%s", "name": "Battle Science"}
				""".formatted(UUID.randomUUID().toString().substring(0, 8))));
		UUID teacherId = UUID.fromString(post("/api/v1/employees", """
				{"name": "Quiz Teacher", "designation": "Teacher", "joinDate": "2024-04-01"}
				"""));
		addQuestion(teacherId, "2 + 2 = ?", QuizOption.B);
		addQuestion(teacherId, "Water boils at?", QuizOption.C);
	}

	@Test
	void revealsCorrectOptionToEveryoneAfterAWrongAnswerAndPausesBeforeTheNextQuestion() {
		BattleRoomResponse room = startBattle();
		UUID firstQuestion = room.getCurrentQuestion().getId();
		QuizOption correct = answerKey.get(firstQuestion);

		assertThat(room.getLastResult()).isNull();
		assertThat(room.getCurrentQuestionStartsAt()).isNotNull();

		battleRoomService.buzz(alice, room.getId());
		battleRoomService.submitAnswer(alice, room.getId(), answer(wrongOption(correct)));

		// Bob (a spectator for that question) sees exactly what Alice did
		BattleRoomResponse seenByBob = battleRoomService.getRoom(bob, room.getId());
		BattleQuestionResultResponse result = seenByBob.getLastResult();
		assertThat(result.getQuestionIndex()).isZero();
		assertThat(result.getQuestionId()).isEqualTo(firstQuestion);
		assertThat(result.getOutcome()).isEqualTo(BattleQuestionOutcome.ANSWERED);
		assertThat(result.getAnsweredByStudentId()).isEqualTo(alice.getOwnerId());
		assertThat(result.getAnsweredByName()).isEqualTo("Alice Reveal");
		assertThat(result.getSelectedOption()).isEqualTo(wrongOption(correct));
		assertThat(result.getCorrectOption()).isEqualTo(correct);
		assertThat(result.getCorrect()).isFalse();
		assertThat(seenByBob.getLastAnswerCorrect()).isFalse();

		// The next question is in its reveal pause - nobody can buzz yet
		assertThat(seenByBob.getCurrentQuestionIndex()).isEqualTo(1);
		assertThat(seenByBob.getCurrentQuestionStartsAt()).isAfter(Instant.now());
		assertThatThrownBy(() -> battleRoomService.buzz(bob, room.getId()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("hasn't started yet");

		endRevealPause(room.getId());
		assertThat(battleRoomService.buzz(bob, room.getId()).isWon()).isTrue();
	}

	@Test
	void revealsCorrectOptionWhenNobodyAnswersInTime() {
		BattleRoomResponse room = startBattle();
		UUID firstQuestion = room.getCurrentQuestion().getId();

		expireCurrentQuestion(room.getId());
		battleRoomService.sweep();

		BattleQuestionResultResponse result = battleRoomService.getRoom(alice, room.getId()).getLastResult();
		assertThat(result.getOutcome()).isEqualTo(BattleQuestionOutcome.TIMED_OUT);
		assertThat(result.getQuestionId()).isEqualTo(firstQuestion);
		assertThat(result.getCorrectOption()).isEqualTo(answerKey.get(firstQuestion));
		assertThat(result.getAnsweredByStudentId()).isNull();
		assertThat(result.getSelectedOption()).isNull();
		assertThat(result.getCorrect()).isNull();
	}

	@Test
	void finalQuestionIsRevealedOnceTheBattleCompletes() {
		BattleRoomResponse room = startBattle();

		UUID firstQuestion = room.getCurrentQuestion().getId();
		battleRoomService.buzz(alice, room.getId());
		battleRoomService.submitAnswer(alice, room.getId(), answer(answerKey.get(firstQuestion)));
		endRevealPause(room.getId());

		UUID lastQuestion = battleRoomService.getRoom(bob, room.getId()).getCurrentQuestion().getId();
		battleRoomService.buzz(bob, room.getId());
		battleRoomService.submitAnswer(bob, room.getId(), answer(answerKey.get(lastQuestion)));

		BattleRoomResponse finished = battleRoomService.getRoom(alice, room.getId());
		assertThat(finished.getStatus()).isEqualTo(BattleRoomStatus.COMPLETED);
		assertThat(finished.getCurrentQuestionStartsAt()).isNull();
		BattleQuestionResultResponse result = finished.getLastResult();
		assertThat(result.getQuestionIndex()).isEqualTo(1);
		assertThat(result.getQuestionId()).isEqualTo(lastQuestion);
		assertThat(result.getAnsweredByStudentId()).isEqualTo(bob.getOwnerId());
		assertThat(result.getCorrect()).isTrue();
		assertThat(result.getCorrectOption()).isEqualTo(answerKey.get(lastQuestion));
	}

	private BattleRoomResponse startBattle() {
		CreateBattleRoomRequest create = new CreateBattleRoomRequest();
		create.setSubjectId(subjectId);
		BattleRoomResponse room = battleRoomService.createRoom(alice, create);
		battleRoomService.joinRoom(bob, room.getId());
		return battleRoomService.startRoomNow(alice, room.getId());
	}

	private void endRevealPause(UUID roomId) {
		setQuestionStartedAt(roomId, Instant.now().minusSeconds(1));
	}

	private void expireCurrentQuestion(UUID roomId) {
		setQuestionStartedAt(roomId, Instant.now().minusSeconds(3600));
	}

	private void setQuestionStartedAt(UUID roomId, Instant startedAt) {
		BattleRoom room = battleRoomRepository.findById(roomId).orElseThrow();
		room.setQuestionStartedAt(startedAt);
		battleRoomRepository.save(room);
	}

	private void addQuestion(UUID teacherId, String text, QuizOption correct) {
		QuizQuestion question = new QuizQuestion();
		question.setSchoolId(SCHOOL_ID);
		question.setSubject(subjectRepository.getReferenceById(subjectId));
		question.setQuestionText(text);
		question.setOptionA("Option A");
		question.setOptionB("Option B");
		question.setOptionC("Option C");
		question.setOptionD("Option D");
		question.setCorrectOption(correct);
		question.setCreatedByTeacher(employeeRepository.getReferenceById(teacherId));
		answerKey.put(quizQuestionRepository.save(question).getId(), correct);
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
		String response = mockMvc.perform(MockMvcRequestBuilders.post(url)
						.header("X-School-Id", SCHOOL_ID.toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.data.id");
	}

	private static SubmitBattleAnswerRequest answer(QuizOption option) {
		SubmitBattleAnswerRequest request = new SubmitBattleAnswerRequest();
		request.setSelectedOption(option);
		return request;
	}

	private static QuizOption wrongOption(QuizOption correct) {
		return correct == QuizOption.A ? QuizOption.B : QuizOption.A;
	}

}
