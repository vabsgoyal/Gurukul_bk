package com.gurukul.notifications;

import com.gurukul.attendance.service.AbsenceAlertService;
import com.gurukul.auth.AuthTestSupport;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.security.JwtService;
import com.gurukul.fees.entity.FeeAssessmentStatus;
import com.gurukul.fees.entity.StudentFeeAssessment;
import com.gurukul.fees.repository.StudentFeeAssessmentRepository;
import com.gurukul.fees.service.FeeDueAlertService;
import com.gurukul.notifications.entity.Notification;
import com.gurukul.notifications.repository.NotificationRepository;
import com.gurukul.parents.ParentTestSupport;
import com.gurukul.parents.repository.ParentRepository;
import com.gurukul.parents.repository.ParentStudentLinkRepository;
import com.gurukul.students.repository.StudentRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Absence alert de-dup, fee-due windows, and the notification inbox (own rows only). */
@SpringBootTest
@AutoConfigureMockMvc
class ParentAlertsIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	/** No real Expo calls from tests - same bean override as ParentChatIntegrationTest (one shared context). */
	@MockitoBean(name = "expoPushRestClient") private RestClient expoPushRestClient;

	@Autowired private MockMvc mockMvc;
	@Autowired private JwtService jwtService;
	@Autowired private ParentRepository parentRepository;
	@Autowired private ParentStudentLinkRepository parentStudentLinkRepository;
	@Autowired private NotificationRepository notificationRepository;
	@Autowired private StudentFeeAssessmentRepository assessmentRepository;
	@Autowired private StudentRepository studentRepository;
	@Autowired private FeeDueAlertService feeDueAlertService;

	private String sectionId;
	private String classTeacherBearer;
	private String child;
	private String sibling;
	private UUID parent;
	private UUID otherParent;
	private String parentBearer;
	private String otherParentBearer;

	@BeforeEach
	void setUp() throws Exception {
		String sfx = UUID.randomUUID().toString().substring(0, 8);
		String adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		sectionId = ParentTestSupport.createSection(mockMvc, SCHOOL_ID, "PAL-" + sfx, "A");
		String teacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Alerts Teacher " + sfx);
		classTeacherBearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "employees", teacherId, "TEACHER");
		ParentTestSupport.assignClassTeacher(mockMvc, SCHOOL_ID, adminBearer, sectionId, teacherId);
		child = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, "Alert Child " + sfx);
		sibling = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, "Alert Sibling " + sfx);
		parent = ParentTestSupport.createParent(parentRepository, parentStudentLinkRepository, SCHOOL_ID, "Alert Parent " + sfx, child, sibling);
		otherParent = ParentTestSupport.createParent(parentRepository, parentStudentLinkRepository, SCHOOL_ID, "Other Parent " + sfx);
		parentBearer = ParentTestSupport.parentBearer(jwtService, SCHOOL_ID, parent);
		otherParentBearer = ParentTestSupport.parentBearer(jwtService, SCHOOL_ID, otherParent);
	}

	@Test
	void absenceAlertsOncePerChildPerDayAcrossCorrections() throws Exception {
		LocalDate today = LocalDate.now(AbsenceAlertService.SCHOOL_ZONE);

		mark(today, "ABSENT", "PRESENT");
		assertThat(alertsFor(parent, AbsenceAlertService.TYPE)).hasSize(1);
		assertThat(alertsFor(parent, AbsenceAlertService.TYPE).get(0).getData()).contains(child);

		// Re-saving the register, and ABSENT -> PRESENT -> ABSENT, never alerts a second time.
		mark(today, "ABSENT", "PRESENT");
		mark(today, "PRESENT", "PRESENT");
		mark(today, "ABSENT", "PRESENT");
		assertThat(alertsFor(parent, AbsenceAlertService.TYPE)).hasSize(1);

		// The sibling being absent is a separate child: one more alert, still once.
		mark(today, "ABSENT", "ABSENT");
		mark(today, "ABSENT", "ABSENT");
		assertThat(alertsFor(parent, AbsenceAlertService.TYPE)).hasSize(2);
	}

	@Test
	void noAbsenceAlertForPresentOrForBackfillingAPastDay() throws Exception {
		LocalDate today = LocalDate.now(AbsenceAlertService.SCHOOL_ZONE);
		mark(today, "PRESENT", "LATE");
		mark(today.minusDays(1), "ABSENT", "ABSENT");
		assertThat(alertsFor(parent, AbsenceAlertService.TYPE)).isEmpty();
	}

	@Test
	void feeDueAlertsThreeDaysBeforeThenWeeklyWhileOverdueAndIdempotentPerWindow() {
		LocalDate due = LocalDate.of(2031, 6, 10);
		UUID assessmentId = assessment(child, "2031-32", due, "5000", "1000", FeeAssessmentStatus.PARTIAL);
		assessment(sibling, "2031-32", due, "5000", "5000", FeeAssessmentStatus.PAID);

		feeDueAlertService.sendDueAlerts(due.minusDays(5));
		assertThat(alertsFor(parent, FeeDueAlertService.TYPE)).isEmpty();

		feeDueAlertService.sendDueAlerts(due.minusDays(3));
		feeDueAlertService.sendDueAlerts(due.minusDays(2));
		feeDueAlertService.sendDueAlerts(due);
		List<Notification> alerts = alertsFor(parent, FeeDueAlertService.TYPE);
		assertThat(alerts).hasSize(1);
		assertThat(alerts.get(0).getBody()).contains("₹4000").contains("is due on 10 Jun 2031");
		assertThat(alerts.get(0).getData()).contains(assessmentId.toString());

		feeDueAlertService.sendDueAlerts(due.plusDays(1));
		feeDueAlertService.sendDueAlerts(due.plusDays(1));
		feeDueAlertService.sendDueAlerts(due.plusDays(4));
		assertThat(alertsFor(parent, FeeDueAlertService.TYPE)).hasSize(2);

		feeDueAlertService.sendDueAlerts(due.plusDays(8));
		assertThat(alertsFor(parent, FeeDueAlertService.TYPE)).hasSize(3);
		assertThat(alertsFor(parent, FeeDueAlertService.TYPE).get(0).getBody()).contains("is overdue");
	}

	@Test
	void inboxListsOnlyMyOwnRowsAndTracksRead() throws Exception {
		mark(LocalDate.now(AbsenceAlertService.SCHOOL_ZONE), "ABSENT", "ABSENT");

		String body = mockMvc.perform(get("/api/v1/notifications")
						.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "Bearer " + parentBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.notifications.length()").value(2))
				.andExpect(jsonPath("$.data.notifications[0].type").value("ABSENCE_ALERT"))
				.andExpect(jsonPath("$.data.notifications[0].data.studentId").exists())
				.andExpect(jsonPath("$.data.hasMore").value(false))
				.andReturn().getResponse().getContentAsString();
		String firstId = JsonPath.read(body, "$.data.notifications[0].id");
		unread(parentBearer, 2);

		// Someone else can neither see nor mark my row.
		mockMvc.perform(get("/api/v1/notifications")
						.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherParentBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.notifications.length()").value(0));
		mockMvc.perform(post("/api/v1/notifications/" + firstId + "/read")
						.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherParentBearer))
				.andExpect(status().isNotFound());
		unread(parentBearer, 2);

		mockMvc.perform(post("/api/v1/notifications/" + firstId + "/read")
						.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "Bearer " + parentBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.readAt").exists());
		unread(parentBearer, 1);

		mockMvc.perform(post("/api/v1/notifications/read-all")
						.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "Bearer " + parentBearer))
				.andExpect(status().isOk());
		unread(parentBearer, 0);

		mockMvc.perform(get("/api/v1/notifications").header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isUnauthorized());
	}

	private void mark(LocalDate date, String childStatus, String siblingStatus) throws Exception {
		mockMvc.perform(post("/api/v1/class-sections/" + sectionId + "/attendance")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + classTeacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"date": "%s", "records": [
								  {"studentId": "%s", "status": "%s"},
								  {"studentId": "%s", "status": "%s"}
								]}
								""".formatted(date, child, childStatus, sibling, siblingStatus)))
				.andExpect(status().isOk());
	}

	private void unread(String bearer, int expected) throws Exception {
		mockMvc.perform(get("/api/v1/notifications/unread-count")
						.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.unread").value(expected));
	}

	private List<Notification> alertsFor(UUID parentId, String type) {
		return notificationRepository.findAllBySchoolIdAndRecipientOwnerTypeAndRecipientOwnerIdOrderByCreatedAtDesc(
						UUID.fromString(SCHOOL_ID), OwnerType.PARENT, parentId, PageRequest.of(0, 100))
				.getContent().stream()
				.filter(n -> n.getType().equals(type))
				.toList();
	}

	private UUID assessment(String studentId, String year, LocalDate due, String totalDue, String totalPaid,
			FeeAssessmentStatus status) {
		StudentFeeAssessment assessment = new StudentFeeAssessment();
		assessment.setSchoolId(UUID.fromString(SCHOOL_ID));
		assessment.setStudent(studentRepository.findById(UUID.fromString(studentId)).orElseThrow());
		assessment.setAcademicYear(year);
		assessment.setDueDate(due);
		assessment.setTotalDue(new BigDecimal(totalDue));
		assessment.setTotalPaid(new BigDecimal(totalPaid));
		assessment.setStatus(status);
		return assessmentRepository.save(assessment).getId();
	}

}
