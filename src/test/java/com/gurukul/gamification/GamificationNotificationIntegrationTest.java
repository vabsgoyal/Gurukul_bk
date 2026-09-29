package com.gurukul.gamification;

import com.gurukul.academics.repository.SubjectRepository;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.gamification.dto.ArenaDtos.CreateChallengeRequest;
import com.gurukul.gamification.dto.ArenaDtos.ChallengeSummaryResponse;
import com.gurukul.gamification.dto.BattleRoomDtos.BattleRoomResponse;
import com.gurukul.gamification.dto.BattleRoomDtos.CreateBattleRoomRequest;
import com.gurukul.gamification.entity.QuizOption;
import com.gurukul.gamification.entity.QuizQuestion;
import com.gurukul.gamification.repository.QuizQuestionRepository;
import com.gurukul.gamification.service.ArenaService;
import com.gurukul.gamification.service.BattleRoomService;
import com.gurukul.notifications.entity.Notification;
import com.gurukul.notifications.repository.NotificationRepository;
import com.gurukul.students.repository.StudentRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Challenges notify the opponent; a new battle room notifies the rest of the class, at most once per cooldown. */
@SpringBootTest
@AutoConfigureMockMvc
class GamificationNotificationIntegrationTest {

	private static final UUID SCHOOL_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final String CLASS_SECTION_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

	/** No real Expo calls from tests - same bean override as ParentChatIntegrationTest (one shared context). */
	@MockitoBean(name = "expoPushRestClient") private RestClient expoPushRestClient;

	@Autowired private MockMvc mockMvc;
	@Autowired private ArenaService arenaService;
	@Autowired private BattleRoomService battleRoomService;
	@Autowired private NotificationRepository notificationRepository;
	@Autowired private QuizQuestionRepository quizQuestionRepository;
	@Autowired private SubjectRepository subjectRepository;
	@Autowired private EmployeeRepository employeeRepository;
	@Autowired private StudentRepository studentRepository;

	private AuthPrincipal alice;
	private AuthPrincipal bob;
	private UUID subjectId;

	@BeforeEach
	void setUp() throws Exception {
		alice = student("Alice Notify");
		bob = student("Bob Notify");
		subjectId = UUID.fromString(post("/api/v1/subjects", """
				{"code": "GN-%s", "name": "Notify Maths"}
				""".formatted(UUID.randomUUID().toString().substring(0, 8))));
		UUID teacherId = UUID.fromString(post("/api/v1/employees", """
				{"name": "Quiz Teacher", "designation": "Teacher", "joinDate": "2024-04-01"}
				"""));
		String className = studentRepository.findByIdAndSchoolId(alice.getOwnerId(), SCHOOL_ID).orElseThrow()
				.getClassSection().getClassName();
		for (int i = 0; i < 5; i++) {
			addQuestion(teacherId, className, "Question " + i);
		}
	}

	@Test
	void challengingAClassmateNotifiesThem() throws Exception {
		CreateChallengeRequest request = new CreateChallengeRequest();
		request.setOpponentStudentId(bob.getOwnerId());
		request.setSubjectId(subjectId);
		ChallengeSummaryResponse challenge = arenaService.createChallenge(alice, request);

		Notification notification = awaitOne(bob, "QUIZ_CHALLENGE");
		assertThat(notification.getTitle()).isEqualTo("Alice Notify challenged you!");
		assertThat(notification.getBody()).contains("Notify Maths");
		assertThat(notification.getData()).contains(challenge.getId().toString());
		assertThat(notificationsFor(alice, "QUIZ_CHALLENGE")).isEmpty();
	}

	@Test
	void openingABattleRoomNotifiesClassmatesButNotTheCreator() throws Exception {
		BattleRoomResponse room = openRoom(alice);

		Notification notification = awaitOne(bob, "BATTLE_ROOM_OPEN");
		assertThat(notification.getTitle()).isEqualTo("Alice Notify opened a Notify Maths battle");
		assertThat(notification.getData()).contains(room.getId().toString(), room.getRoomCode());
		assertThat(notificationsFor(alice, "BATTLE_ROOM_OPEN")).isEmpty();
	}

	@Test
	void aClassOpeningSeveralRoomsNotifiesEachStudentOncePerCooldown() throws Exception {
		openRoom(alice);
		awaitOne(bob, "BATTLE_ROOM_OPEN");
		openRoom(alice);
		Thread.sleep(500);

		assertThat(notificationsFor(bob, "BATTLE_ROOM_OPEN")).hasSize(1);
	}

	private BattleRoomResponse openRoom(AuthPrincipal creator) {
		CreateBattleRoomRequest request = new CreateBattleRoomRequest();
		request.setSubjectId(subjectId);
		return battleRoomService.createRoom(creator, request);
	}

	/** Notifications go out asynchronously after commit. */
	private Notification awaitOne(AuthPrincipal student, String type) throws InterruptedException {
		for (int i = 0; i < 50; i++) {
			List<Notification> found = notificationsFor(student, type);
			if (!found.isEmpty()) {
				assertThat(found).hasSize(1);
				return found.getFirst();
			}
			Thread.sleep(100);
		}
		throw new AssertionError("No " + type + " notification for " + student.getUsername());
	}

	private List<Notification> notificationsFor(AuthPrincipal student, String type) {
		return notificationRepository.findAll().stream()
				.filter(n -> n.getRecipientOwnerType() == OwnerType.STUDENT)
				.filter(n -> n.getRecipientOwnerId().equals(student.getOwnerId()))
				.filter(n -> type.equals(n.getType()))
				.toList();
	}

	private void addQuestion(UUID teacherId, String className, String text) {
		QuizQuestion question = new QuizQuestion();
		question.setSchoolId(SCHOOL_ID);
		question.setSubject(subjectRepository.getReferenceById(subjectId));
		question.setClassName(className);
		question.setQuestionText(text);
		question.setOptionA("Option A");
		question.setOptionB("Option B");
		question.setOptionC("Option C");
		question.setOptionD("Option D");
		question.setCorrectOption(QuizOption.A);
		question.setCreatedByTeacher(employeeRepository.getReferenceById(teacherId));
		quizQuestionRepository.save(question);
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

}
