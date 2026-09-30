package com.gurukul.timetable;

import com.gurukul.auth.AuthTestSupport;
import com.gurukul.auth.entity.Credential;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.JwtService;
import com.gurukul.parents.entity.Parent;
import com.gurukul.parents.entity.ParentStudentLink;
import com.gurukul.parents.repository.ParentRepository;
import com.gurukul.parents.repository.ParentStudentLinkRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Timetable: bell schedule validation, per-section bulk save (assignment check, clash detection,
 * atomicity), and role-scoped reads. Every test builds its own sections/teachers so they don't
 * interfere; the bell schedule is school-wide, so it's reset to the same shape before each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TimetableIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	/** Periods 1-4 teaching, 5 lunch break, 6-8 teaching. Saturday off. */
	private static final String STANDARD_SCHEDULE = """
			{"saturdayEnabled": %s, "periods": [
			  {"periodNumber": 1, "startTime": "08:00", "endTime": "08:40"},
			  {"periodNumber": 2, "startTime": "08:40", "endTime": "09:20"},
			  {"periodNumber": 3, "startTime": "09:20", "endTime": "10:00"},
			  {"periodNumber": 4, "startTime": "10:00", "endTime": "10:40"},
			  {"periodNumber": 5, "startTime": "10:40", "endTime": "11:10", "breakPeriod": true, "label": "Lunch"},
			  {"periodNumber": 6, "startTime": "11:10", "endTime": "11:50"},
			  {"periodNumber": 7, "startTime": "11:50", "endTime": "12:30"},
			  {"periodNumber": 8, "startTime": "12:30", "endTime": "13:10"}
			]}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private ParentRepository parentRepository;

	@Autowired
	private ParentStudentLinkRepository parentStudentLinkRepository;

	private String admin;

	@BeforeEach
	void resetBellSchedule() throws Exception {
		admin = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		putSchedule(admin, STANDARD_SCHEDULE.formatted("false")).andExpect(status().isOk());
	}

	// ------------------------------------------------------------------ bell schedule

	@Test
	void bellScheduleRoundTripsAndIsReadableByAnyRole() throws Exception {
		Fixture f = fixture();
		String student = studentLogin(f.sectionA);

		mockMvc.perform(get("/api/v1/periods").header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, bearer(student)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.saturdayEnabled").value(false))
				.andExpect(jsonPath("$.data.days", hasSize(5)))
				.andExpect(jsonPath("$.data.periods", hasSize(8)))
				.andExpect(jsonPath("$.data.periods[0].startTime").value("08:00"))
				.andExpect(jsonPath("$.data.periods[4].breakPeriod").value(true))
				.andExpect(jsonPath("$.data.periods[4].label").value("Lunch"));
	}

	@Test
	void bellScheduleRejectsOverlapsBadTimesAndDuplicates() throws Exception {
		putSchedule(admin, """
				{"saturdayEnabled": false, "periods": [
				  {"periodNumber": 1, "startTime": "08:00", "endTime": "08:45"},
				  {"periodNumber": 2, "startTime": "08:40", "endTime": "09:20"}
				]}
				""").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("overlaps")));
		putSchedule(admin, """
				{"saturdayEnabled": false, "periods": [{"periodNumber": 1, "startTime": "09:00", "endTime": "08:00"}]}
				""").andExpect(status().isBadRequest());
		putSchedule(admin, """
				{"saturdayEnabled": false, "periods": [
				  {"periodNumber": 1, "startTime": "08:00", "endTime": "08:40"},
				  {"periodNumber": 1, "startTime": "09:00", "endTime": "09:40"}
				]}
				""").andExpect(status().isBadRequest());
		putSchedule(admin, """
				{"saturdayEnabled": false, "periods": []}
				""").andExpect(status().isBadRequest());
		putSchedule(admin, """
				{"saturdayEnabled": false, "periods": [{"periodNumber": 1, "startTime": "8.00", "endTime": "08:40"}]}
				""").andExpect(status().isBadRequest());

		// Nothing above was saved.
		mockMvc.perform(get("/api/v1/periods").header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(jsonPath("$.data.periods", hasSize(8)));
	}

	@Test
	void onlyAdminCanChangeBellSchedule() throws Exception {
		Fixture f = fixture();
		putSchedule(f.teacher1Login, STANDARD_SCHEDULE.formatted("false")).andExpect(status().isForbidden());
		putSchedule(studentLogin(f.sectionA), STANDARD_SCHEDULE.formatted("false")).andExpect(status().isForbidden());
		mockMvc.perform(put("/api/v1/periods").header("X-School-Id", SCHOOL_ID).header("Authorization", "")
						.contentType(MediaType.APPLICATION_JSON).content(STANDARD_SCHEDULE.formatted("false")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void bellScheduleCannotOrphanSavedSlots() throws Exception {
		Fixture f = fixture();
		putTimetable(admin, f.sectionA, slots(slot("MONDAY", 8, f.mathId, f.teacher1))).andExpect(status().isOk());

		// Removing period 8, or turning it into a break, would orphan the Monday slot.
		String withoutPeriod8 = STANDARD_SCHEDULE.formatted("false")
				.replace(",\n  {\"periodNumber\": 8, \"startTime\": \"12:30\", \"endTime\": \"13:10\"}", "");
		putSchedule(admin, withoutPeriod8).andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("TIMETABLE_IN_USE"));
		String period8Break = STANDARD_SCHEDULE.formatted("false")
				.replace("{\"periodNumber\": 8, \"startTime\": \"12:30\", \"endTime\": \"13:10\"}",
						"{\"periodNumber\": 8, \"startTime\": \"12:30\", \"endTime\": \"13:10\", \"breakPeriod\": true}");
		putSchedule(admin, period8Break).andExpect(status().isConflict());

		putTimetable(admin, f.sectionA, slots()).andExpect(status().isOk());
	}

	@Test
	void saturdayIsSwitchable() throws Exception {
		Fixture f = fixture();
		putTimetable(admin, f.sectionA, slots(slot("SATURDAY", 1, f.mathId, f.teacher1)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Saturday")));
		putTimetable(admin, f.sectionA, slots(slot("SUNDAY", 1, f.mathId, f.teacher1))).andExpect(status().isBadRequest());

		putSchedule(admin, STANDARD_SCHEDULE.formatted("true")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.days", hasSize(6)));
		putTimetable(admin, f.sectionA, slots(slot("SATURDAY", 1, f.mathId, f.teacher1))).andExpect(status().isOk());

		// Can't switch Saturday off while a Saturday slot exists...
		putSchedule(admin, STANDARD_SCHEDULE.formatted("false")).andExpect(status().isConflict());
		// ...until it's cleared.
		putTimetable(admin, f.sectionA, slots()).andExpect(status().isOk());
		putSchedule(admin, STANDARD_SCHEDULE.formatted("false")).andExpect(status().isOk());
	}

	// ------------------------------------------------------------------ section timetable: save

	@Test
	void adminSavesAndReadsSectionTimetable() throws Exception {
		Fixture f = fixture();
		putTimetable(admin, f.sectionA, slots(
				slot("MONDAY", 1, f.mathId, f.teacher1),
				slot("MONDAY", 2, f.scienceId, f.teacher2),
				slot("TUESDAY", 1, f.scienceId, f.teacher2)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.scope").value("SECTION"))
				.andExpect(jsonPath("$.data.slots", hasSize(3)))
				.andExpect(jsonPath("$.data.slots[0].dayOfWeek").value("MONDAY"))
				.andExpect(jsonPath("$.data.slots[0].periodNumber").value(1))
				.andExpect(jsonPath("$.data.slots[0].subjectName").value("Maths"));

		// Re-saving replaces the whole week rather than merging.
		putTimetable(admin, f.sectionA, slots(slot("FRIDAY", 3, f.mathId, f.teacher1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.slots", hasSize(1)));
		getTimetable(admin, f.sectionA)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.slots", hasSize(1)))
				.andExpect(jsonPath("$.data.slots[0].dayOfWeek").value("FRIDAY"))
				.andExpect(jsonPath("$.data.periods", hasSize(8)));
	}

	@Test
	void teacherClashWithAnotherSectionIsRejectedWithDetailsAndNothingSaved() throws Exception {
		Fixture f = fixture();
		putTimetable(admin, f.sectionA, slots(slot("MONDAY", 1, f.mathId, f.teacher1))).andExpect(status().isOk());
		putTimetable(admin, f.sectionB, slots(slot("MONDAY", 2, f.mathId, f.teacher1))).andExpect(status().isOk());

		// teacher1 is already in section A on Monday period 1.
		putTimetable(admin, f.sectionB, slots(
				slot("MONDAY", 1, f.mathId, f.teacher1),
				slot("WEDNESDAY", 1, f.mathId, f.teacher1)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.errorCode").value("TIMETABLE_CLASH"))
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].dayOfWeek").value("MONDAY"))
				.andExpect(jsonPath("$.data[0].periodNumber").value(1))
				.andExpect(jsonPath("$.data[0].teacherId").value(f.teacher1))
				.andExpect(jsonPath("$.data[0].conflictingSectionId").value(f.sectionA));

		// Section B still has its previous week, untouched.
		getTimetable(admin, f.sectionB)
				.andExpect(jsonPath("$.data.slots", hasSize(1)))
				.andExpect(jsonPath("$.data.slots[0].periodNumber").value(2));

		// Re-saving section A with its own existing slot is not a clash with itself.
		putTimetable(admin, f.sectionA, slots(slot("MONDAY", 1, f.mathId, f.teacher1))).andExpect(status().isOk());
	}

	@Test
	void sameTeacherInAnotherAcademicYearIsNotAClash() throws Exception {
		Fixture f = fixture();
		String oldSection = createSection("2025-26");
		assign(oldSection, f.mathId, f.teacher1);
		putTimetable(admin, oldSection, slots(slot("MONDAY", 1, f.mathId, f.teacher1))).andExpect(status().isOk());
		putTimetable(admin, f.sectionA, slots(slot("MONDAY", 1, f.mathId, f.teacher1))).andExpect(status().isOk());
	}

	@Test
	void duplicateDayPeriodInPayloadIsRejected() throws Exception {
		Fixture f = fixture();
		putTimetable(admin, f.sectionA, slots(
				slot("MONDAY", 1, f.mathId, f.teacher1),
				slot("MONDAY", 1, f.scienceId, f.teacher2)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("more than once")));
	}

	@Test
	void subjectTeacherPairMustBeAssignedToTheSection() throws Exception {
		Fixture f = fixture();
		// teacher2 teaches science in section A, not maths.
		putTimetable(admin, f.sectionA, slots(slot("MONDAY", 1, f.mathId, f.teacher2)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not assigned")));
		// science isn't assigned to section B at all.
		putTimetable(admin, f.sectionB, slots(slot("MONDAY", 1, f.scienceId, f.teacher2)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void breakAndUndefinedPeriodsCannotHoldASubject() throws Exception {
		Fixture f = fixture();
		putTimetable(admin, f.sectionA, slots(slot("MONDAY", 5, f.mathId, f.teacher1)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("break")));
		putTimetable(admin, f.sectionA, slots(slot("MONDAY", 12, f.mathId, f.teacher1)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void onlyAdminCanSaveASectionTimetable() throws Exception {
		Fixture f = fixture();
		String body = slots(slot("MONDAY", 1, f.mathId, f.teacher1));
		putTimetable(f.teacher1Login, f.sectionA, body).andExpect(status().isForbidden());
		putTimetable(f.classTeacherLogin, f.sectionA, body).andExpect(status().isForbidden());
		putTimetable(studentLogin(f.sectionA), f.sectionA, body).andExpect(status().isForbidden());
		putTimetable(parentLogin(createStudentIn(f.sectionA)), f.sectionA, body).andExpect(status().isForbidden());
	}

	// ------------------------------------------------------------------ reads & role scoping

	@Test
	void sectionTimetableReadAccessIsScoped() throws Exception {
		Fixture f = fixture();
		putTimetable(admin, f.sectionA, slots(slot("MONDAY", 1, f.mathId, f.teacher1))).andExpect(status().isOk());

		// Class teacher of A (teaches no subject there) and a subject teacher of A may view it.
		getTimetable(f.classTeacherLogin, f.sectionA).andExpect(status().isOk());
		getTimetable(f.teacher2Login, f.sectionA).andExpect(status().isOk());
		// A teacher with no link to section B may not.
		getTimetable(f.teacher2Login, f.sectionB).andExpect(status().isForbidden());
		getTimetable(f.classTeacherLogin, f.sectionB).andExpect(status().isForbidden());

		String studentA = createStudentIn(f.sectionA);
		String studentLogin = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, admin, "students", studentA, "STUDENT");
		getTimetable(studentLogin, f.sectionA).andExpect(status().isOk()).andExpect(jsonPath("$.data.slots", hasSize(1)));
		getTimetable(studentLogin, f.sectionB).andExpect(status().isForbidden());

		String parent = parentLogin(studentA);
		getTimetable(parent, f.sectionA).andExpect(status().isOk());
		getTimetable(parent, f.sectionB).andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/class-sections/" + f.sectionA + "/timetable").header("X-School-Id", SCHOOL_ID)
						.header("Authorization", "")).andExpect(status().isUnauthorized());
	}

	@Test
	void myTimetableForTeacherSpansSections() throws Exception {
		Fixture f = fixture();
		putTimetable(admin, f.sectionA, slots(
				slot("MONDAY", 1, f.mathId, f.teacher1),
				slot("MONDAY", 2, f.scienceId, f.teacher2))).andExpect(status().isOk());
		putTimetable(admin, f.sectionB, slots(slot("TUESDAY", 3, f.mathId, f.teacher1))).andExpect(status().isOk());

		myTimetable(f.teacher1Login, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.scope").value("TEACHER"))
				.andExpect(jsonPath("$.data.teacherId").value(f.teacher1))
				.andExpect(jsonPath("$.data.slots", hasSize(2)))
				.andExpect(jsonPath("$.data.slots[0].sectionId").value(f.sectionA))
				.andExpect(jsonPath("$.data.slots[1].sectionId").value(f.sectionB));
	}

	@Test
	void myTimetableForTeacherWithNoSlotsIsEmptyNotAnError() throws Exception {
		Fixture f = fixture();
		myTimetable(f.classTeacherLogin, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.scope").value("TEACHER"))
				.andExpect(jsonPath("$.data.slots", hasSize(0)))
				.andExpect(jsonPath("$.data.periods", hasSize(8)));
	}

	@Test
	void myTimetableForStudentIsOwnSection() throws Exception {
		Fixture f = fixture();
		putTimetable(admin, f.sectionA, slots(slot("MONDAY", 1, f.mathId, f.teacher1))).andExpect(status().isOk());
		myTimetable(studentLogin(f.sectionA), null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.scope").value("SECTION"))
				.andExpect(jsonPath("$.data.sectionId").value(f.sectionA))
				.andExpect(jsonPath("$.data.slots[0].teacherName").value("T1 " + f.suffix));
	}

	@Test
	void myTimetableForParentIsPerChild() throws Exception {
		Fixture f = fixture();
		putTimetable(admin, f.sectionA, slots(slot("MONDAY", 1, f.mathId, f.teacher1))).andExpect(status().isOk());
		putTimetable(admin, f.sectionB, slots(slot("TUESDAY", 1, f.mathId, f.teacher1))).andExpect(status().isOk());
		String childA = createStudentIn(f.sectionA);
		String childB = createStudentIn(f.sectionB);
		String stranger = createStudentIn(f.sectionB);

		UUID parentId = createParent(childA);
		String parent = parentToken(parentId);
		// One child: childId is optional.
		myTimetable(parent, null).andExpect(status().isOk()).andExpect(jsonPath("$.data.sectionId").value(f.sectionA));

		link(parentId, childB);
		// Two children: must choose.
		myTimetable(parent, null).andExpect(status().isBadRequest());
		myTimetable(parent, childB).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.sectionId").value(f.sectionB))
				.andExpect(jsonPath("$.data.slots[0].dayOfWeek").value("TUESDAY"));
		// Not their child.
		myTimetable(parent, stranger).andExpect(status().isForbidden());
	}

	// ------------------------------------------------------------------ fixture helpers

	/** Two sections (2026-27), maths+science, teacher1 teaches maths in A and B, teacher2 science in A, a class teacher of A. */
	private record Fixture(String suffix, String sectionA, String sectionB, String mathId, String scienceId,
			String teacher1, String teacher2, String teacher1Login, String teacher2Login, String classTeacherLogin) {
	}

	private Fixture fixture() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String sectionA = createSection("2026-27");
		String sectionB = createSection("2026-27");
		String mathId = createSubject("Maths");
		String scienceId = createSubject("Science");
		String teacher1 = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "T1 " + suffix);
		String teacher2 = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "T2 " + suffix);
		String classTeacher = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "CT " + suffix);
		assign(sectionA, mathId, teacher1);
		assign(sectionB, mathId, teacher1);
		assign(sectionA, scienceId, teacher2);
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.patch("/api/v1/class-sections/" + sectionA + "/class-teacher")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"teacherId\": \"" + classTeacher + "\"}"))
				.andExpect(status().isOk());
		return new Fixture(suffix, sectionA, sectionB, mathId, scienceId, teacher1, teacher2,
				AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, admin, "employees", teacher1, "TEACHER"),
				AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, admin, "employees", teacher2, "TEACHER"),
				AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, admin, "employees", classTeacher, "TEACHER"));
	}

	private String createSection(String academicYear) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 6", "section": "TT-%s", "academicYear": "%s"}
								""".formatted(UUID.randomUUID().toString().substring(0, 8), academicYear)))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
	}

	private String createSubject(String name) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/subjects")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code": "TT-%s", "name": "%s"}
								""".formatted(UUID.randomUUID().toString().substring(0, 8), name)))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
	}

	private void assign(String sectionId, String subjectId, String teacherId) throws Exception {
		mockMvc.perform(post("/api/v1/class-sections/" + sectionId + "/subjects")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"subjectId": "%s", "teacherId": "%s"}
								""".formatted(subjectId, teacherId)))
				.andExpect(status().isOk());
	}

	private String createStudentIn(String sectionId) throws Exception {
		return AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, "TT Student " + UUID.randomUUID().toString().substring(0, 6));
	}

	private String studentLogin(String sectionId) throws Exception {
		return AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, admin, "students", createStudentIn(sectionId), "STUDENT");
	}

	private UUID createParent(String childId) {
		Parent parent = new Parent();
		parent.setSchoolId(UUID.fromString(SCHOOL_ID));
		parent.setName("TT Parent");
		UUID parentId = parentRepository.save(parent).getId();
		link(parentId, childId);
		return parentId;
	}

	private void link(UUID parentId, String childId) {
		ParentStudentLink link = new ParentStudentLink();
		link.setSchoolId(UUID.fromString(SCHOOL_ID));
		link.setParentId(parentId);
		link.setStudentId(UUID.fromString(childId));
		parentStudentLinkRepository.save(link);
	}

	/** Parents log in via OTP in the app; here we mint the same JWT directly for a seeded parent. */
	private String parentToken(UUID parentId) {
		Credential credential = new Credential();
		credential.setSchoolId(UUID.fromString(SCHOOL_ID));
		credential.setOwnerType(OwnerType.PARENT);
		credential.setOwnerId(parentId);
		credential.setRole(Role.PARENT);
		credential.setUsername("tt-parent-" + parentId);
		return jwtService.generateToken(credential);
	}

	private String parentLogin(String childId) {
		return parentToken(createParent(childId));
	}

	private static String slot(String day, int period, String subjectId, String teacherId) {
		return """
				{"dayOfWeek": "%s", "periodNumber": %d, "subjectId": "%s", "teacherId": "%s"}""".formatted(day, period, subjectId, teacherId);
	}

	private static String slots(String... slots) {
		return "{\"slots\": [" + String.join(",", slots) + "]}";
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	private ResultActions putSchedule(String token, String body) throws Exception {
		return mockMvc.perform(put("/api/v1/periods")
				.header("X-School-Id", SCHOOL_ID)
				.header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private ResultActions putTimetable(String token, String sectionId, String body) throws Exception {
		return mockMvc.perform(put("/api/v1/class-sections/" + sectionId + "/timetable")
				.header("X-School-Id", SCHOOL_ID)
				.header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private ResultActions getTimetable(String token, String sectionId) throws Exception {
		return mockMvc.perform(get("/api/v1/class-sections/" + sectionId + "/timetable")
				.header("X-School-Id", SCHOOL_ID)
				.header(HttpHeaders.AUTHORIZATION, bearer(token)));
	}

	private ResultActions myTimetable(String token, String childId) throws Exception {
		var request = get("/api/v1/timetable/me")
				.header("X-School-Id", SCHOOL_ID)
				.header(HttpHeaders.AUTHORIZATION, bearer(token));
		if (childId != null) {
			request = request.param("childId", childId);
		}
		return mockMvc.perform(request);
	}

}
