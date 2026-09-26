package com.gurukul.gamification;

import com.gurukul.academics.repository.SubjectRepository;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.gamification.dto.BattleRoomDtos.BattleParticipantResponse;
import com.gurukul.gamification.dto.BattleRoomDtos.BattlePlayerResultResponse;
import com.gurukul.gamification.dto.BattleRoomDtos.BattleQuestionResultResponse;
import com.gurukul.gamification.dto.BattleRoomDtos.BattleRoomResponse;
import com.gurukul.gamification.dto.BattleRoomDtos.CreateBattleRoomRequest;
import com.gurukul.gamification.dto.BattleRoomDtos.SubmitBattleAnswerRequest;
import com.gurukul.gamification.dto.BattleRoomDtos.SubmitBattleAnswerResponse;
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
 * Battle rooms, everyone-answers mode: every participant answers each question inside a 10s window,
 * a correct answer scores 1-10 by speed, and results (with the correct option) are only revealed
 * once the question closes. The scheduled sweep is slowed to hourly so each test drives
 * sweep() itself - otherwise it races the timings the tests set up.
 */
@SpringBootTest(properties = "app.gamification.battle-room.sweep-interval-ms=3600000")
@AutoConfigureMockMvc
class BattleRoomIntegrationTest {

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
		alice = student("Alice Battle");
		bob = student("Bob Battle");
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
	void bothPlayersAnswerAndFasterCorrectAnswersScoreMore() {
		BattleRoomResponse room = startBattle();
		UUID firstQuestion = room.getCurrentQuestion().getId();
		QuizOption correct = answerKey.get(firstQuestion);
		assertThat(room.getCurrentQuestionEndsAt()).isEqualTo(room.getCurrentQuestionStartsAt().plusSeconds(10));

		// Alice answers correctly ~3.2s in: 10 - 3 = 7 points
		startCurrentQuestionAgo(room.getId(), 3200);
		SubmitBattleAnswerResponse ack = battleRoomService.submitAnswer(alice, room.getId(), answer(correct));
		assertThat(ack.getQuestionIndex()).isZero();
		assertThat(ack.isRoomCompleted()).isFalse();

		// Still open for Bob, and nothing gives away whether Alice was right
		BattleRoomResponse whileOpen = battleRoomService.getRoom(bob, room.getId());
		assertThat(whileOpen.getCurrentQuestionIndex()).isZero();
		assertThat(whileOpen.getLastResult()).isNull();
		assertThat(participant(whileOpen, alice).isAnsweredCurrentQuestion()).isTrue();
		assertThat(participant(whileOpen, alice).getPoints()).as("totals only move once the question closes").isZero();
		assertThat(participant(whileOpen, alice).getCorrectCount()).isZero();
		assertThat(participant(whileOpen, bob).isAnsweredCurrentQuestion()).isFalse();

		// Bob's (wrong) answer is the last one, so the question closes immediately
		battleRoomService.submitAnswer(bob, room.getId(), answer(wrongOption(correct)));
		BattleRoomResponse closed = battleRoomService.getRoom(alice, room.getId());
		assertThat(closed.getCurrentQuestionIndex()).isEqualTo(1);

		BattleQuestionResultResponse result = closed.getLastResult();
		assertThat(result.getQuestionId()).isEqualTo(firstQuestion);
		assertThat(result.getCorrectOption()).isEqualTo(correct);
		assertThat(result.getResults()).extracting(BattlePlayerResultResponse::getStudentId)
				.containsExactly(alice.getOwnerId(), bob.getOwnerId());
		BattlePlayerResultResponse aliceResult = result.getResults().get(0);
		assertThat(aliceResult.isAnswered()).isTrue();
		assertThat(aliceResult.isCorrect()).isTrue();
		assertThat(aliceResult.getPoints()).isEqualTo(7);
		assertThat(aliceResult.getResponseMs()).isBetween(3200, 4000);
		BattlePlayerResultResponse bobResult = result.getResults().get(1);
		assertThat(bobResult.isCorrect()).isFalse();
		assertThat(bobResult.getSelectedOption()).isEqualTo(wrongOption(correct));
		assertThat(bobResult.getPoints()).isZero();

		assertThat(participant(closed, alice).getPoints()).isEqualTo(7);
		assertThat(closed.getParticipants().get(0).getStudentId()).isEqualTo(alice.getOwnerId());

		// Next question is in its 3s reveal pause
		assertThat(closed.getCurrentQuestionStartsAt()).isAfter(Instant.now());
		assertThatThrownBy(() -> battleRoomService.submitAnswer(bob, room.getId(), answer(QuizOption.A)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("hasn't started yet");
	}

	@Test
	void rejectsSecondAndLateAnswers() {
		BattleRoomResponse room = startBattle();
		startCurrentQuestionAgo(room.getId(), 0);
		battleRoomService.submitAnswer(alice, room.getId(), answer(QuizOption.A));

		assertThatThrownBy(() -> battleRoomService.submitAnswer(alice, room.getId(), answer(QuizOption.B)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("already answered");

		startCurrentQuestionAgo(room.getId(), 10_500);
		assertThatThrownBy(() -> battleRoomService.submitAnswer(bob, room.getId(), answer(QuizOption.A)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Time's up");
	}

	@Test
	void timeoutClosesTheQuestionAndPlayersWhoDidNotAnswerScoreZero() {
		BattleRoomResponse room = startBattle();
		UUID firstQuestion = room.getCurrentQuestion().getId();
		startCurrentQuestionAgo(room.getId(), 0);
		battleRoomService.submitAnswer(alice, room.getId(), answer(answerKey.get(firstQuestion)));

		startCurrentQuestionAgo(room.getId(), 11_000);
		battleRoomService.sweep();

		BattleRoomResponse closed = battleRoomService.getRoom(bob, room.getId());
		assertThat(closed.getCurrentQuestionIndex()).isEqualTo(1);
		BattleQuestionResultResponse result = closed.getLastResult();
		assertThat(result.getCorrectOption()).isEqualTo(answerKey.get(firstQuestion));
		BattlePlayerResultResponse bobResult = result.getResults().stream()
				.filter(r -> r.getStudentId().equals(bob.getOwnerId())).findFirst().orElseThrow();
		assertThat(bobResult.isAnswered()).isFalse();
		assertThat(bobResult.getSelectedOption()).isNull();
		assertThat(bobResult.getResponseMs()).isNull();
		assertThat(bobResult.getPoints()).isZero();
		assertThat(participant(closed, alice).getPoints()).isEqualTo(10);
	}

	@Test
	void sweepLeavesQuestionsInsideTheirWindowAlone() {
		BattleRoomResponse room = startBattle();
		startCurrentQuestionAgo(room.getId(), 5_000);

		battleRoomService.sweep();

		assertThat(battleRoomService.getRoom(alice, room.getId()).getCurrentQuestionIndex()).isZero();
	}

	@Test
	void mostPointsWinsEvenWithTheSameNumberOfCorrectAnswers() {
		BattleRoomResponse room = startBattle();

		// Q1: both correct - Alice instantly (10), Bob ~8.2s in (2)
		UUID q1 = room.getCurrentQuestion().getId();
		startCurrentQuestionAgo(room.getId(), 0);
		battleRoomService.submitAnswer(alice, room.getId(), answer(answerKey.get(q1)));
		startCurrentQuestionAgo(room.getId(), 8_200);
		battleRoomService.submitAnswer(bob, room.getId(), answer(answerKey.get(q1)));

		// Q2: both correct - Bob instantly (10), Alice ~5.2s in (5). Alice 15 vs Bob 12.
		UUID q2 = battleRoomService.getRoom(alice, room.getId()).getCurrentQuestion().getId();
		startCurrentQuestionAgo(room.getId(), 0);
		battleRoomService.submitAnswer(bob, room.getId(), answer(answerKey.get(q2)));
		startCurrentQuestionAgo(room.getId(), 5_200);
		SubmitBattleAnswerResponse last = battleRoomService.submitAnswer(alice, room.getId(), answer(answerKey.get(q2)));
		assertThat(last.isRoomCompleted()).isTrue();

		BattleRoomResponse finished = battleRoomService.getRoom(bob, room.getId());
		assertThat(finished.getStatus()).isEqualTo(BattleRoomStatus.COMPLETED);
		assertThat(participant(finished, alice).getPoints()).isEqualTo(15);
		assertThat(participant(finished, bob).getPoints()).isEqualTo(12);
		assertThat(participant(finished, alice).getCorrectCount()).isEqualTo(2);
		assertThat(participant(finished, bob).getCorrectCount()).isEqualTo(2);
		assertThat(finished.getWinnerStudentId()).isEqualTo(alice.getOwnerId());
		assertThat(finished.getWinnerName()).isEqualTo("Alice Battle");
		assertThat(finished.getCurrentQuestionStartsAt()).isNull();
		assertThat(finished.getLastResult().getQuestionIndex()).isEqualTo(1);
		assertThat(finished.getLastResult().getQuestionId()).isEqualTo(q2);
	}

	private BattleRoomResponse startBattle() {
		CreateBattleRoomRequest create = new CreateBattleRoomRequest();
		create.setSubjectId(subjectId);
		BattleRoomResponse room = battleRoomService.createRoom(alice, create);
		battleRoomService.joinRoom(bob, room.getId());
		return battleRoomService.startRoomNow(alice, room.getId());
	}

	/** Backdates the current question's start so the next answer lands that many ms into it. */
	private void startCurrentQuestionAgo(UUID roomId, long millis) {
		BattleRoom room = battleRoomRepository.findById(roomId).orElseThrow();
		room.setQuestionStartedAt(Instant.now().minusMillis(millis));
		battleRoomRepository.save(room);
	}

	private static BattleParticipantResponse participant(BattleRoomResponse room, AuthPrincipal student) {
		return room.getParticipants().stream()
				.filter(p -> p.getStudentId().equals(student.getOwnerId())).findFirst().orElseThrow();
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
