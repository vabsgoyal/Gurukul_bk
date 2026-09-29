package com.gurukul.chat;

import com.gurukul.academics.repository.SectionSubjectTeacherRepository;
import com.gurukul.academics.repository.SubjectRepository;
import com.gurukul.auth.AuthTestSupport;
import com.gurukul.auth.security.JwtService;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.parents.ParentTestSupport;
import com.gurukul.parents.repository.ParentRepository;
import com.gurukul.parents.repository.ParentStudentLinkRepository;
import com.gurukul.students.repository.ClassSectionRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parent chat ownership: a parent reaches only their own child's teachers (class teacher or any
 * subject teacher of the child's section) and the school's admins; a teacher reaches only parents of
 * their students; nobody reads a conversation they aren't part of. Plus parent announcement
 * visibility (school-wide + their child's section/grade only).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ParentChatIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	/** No real Expo calls from tests: announcements push to every employee, some of whom other tests gave devices. */
	@MockitoBean(name = "expoPushRestClient") private RestClient expoPushRestClient;

	@Autowired private MockMvc mockMvc;
	@Autowired private JwtService jwtService;
	@Autowired private ParentRepository parentRepository;
	@Autowired private ParentStudentLinkRepository parentStudentLinkRepository;
	@Autowired private SubjectRepository subjectRepository;
	@Autowired private SectionSubjectTeacherRepository sectionSubjectTeacherRepository;
	@Autowired private ClassSectionRepository classSectionRepository;
	@Autowired private EmployeeRepository employeeRepository;

	private String adminBearer;
	private String adminId;
	private String classA;
	private String classB;
	private String sectionA;
	private String sectionB;
	private String classTeacherA;
	private String subjectTeacherA;
	private String classTeacherB;
	private String subjectTeacherABearer;
	private String childA;
	private String childB;
	private UUID parentA;
	private UUID parentB;
	private String parentABearer;
	private String parentBBearer;

	@BeforeEach
	void setUpSchool() throws Exception {
		String sfx = UUID.randomUUID().toString().substring(0, 8);
		adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		adminId = jwtService.parseToken(adminBearer).getOwnerId().toString();
		classA = "PCA-" + sfx;
		classB = "PCB-" + sfx;
		sectionA = ParentTestSupport.createSection(mockMvc, SCHOOL_ID, classA, "A");
		sectionB = ParentTestSupport.createSection(mockMvc, SCHOOL_ID, classB, "A");

		classTeacherA = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Class Teacher A " + sfx);
		subjectTeacherA = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Maths Teacher A " + sfx);
		classTeacherB = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Class Teacher B " + sfx);
		subjectTeacherABearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "employees", subjectTeacherA, "TEACHER");
		AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "employees", classTeacherA, "TEACHER");
		AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "employees", classTeacherB, "TEACHER");
		ParentTestSupport.assignClassTeacher(mockMvc, SCHOOL_ID, adminBearer, sectionA, classTeacherA);
		ParentTestSupport.assignClassTeacher(mockMvc, SCHOOL_ID, adminBearer, sectionB, classTeacherB);
		ParentTestSupport.assignSubjectTeacher(subjectRepository, sectionSubjectTeacherRepository, classSectionRepository,
				employeeRepository, SCHOOL_ID, sectionA, subjectTeacherA, "Maths");

		childA = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionA, "Child A " + sfx);
		childB = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionB, "Child B " + sfx);
		parentA = ParentTestSupport.createParent(parentRepository, parentStudentLinkRepository, SCHOOL_ID, "Parent A " + sfx, childA);
		parentB = ParentTestSupport.createParent(parentRepository, parentStudentLinkRepository, SCHOOL_ID, "Parent B " + sfx, childB);
		parentABearer = ParentTestSupport.parentBearer(jwtService, SCHOOL_ID, parentA);
		parentBBearer = ParentTestSupport.parentBearer(jwtService, SCHOOL_ID, parentB);
	}

	@Test
	void parentCanStartChatsWithTheirChildsClassTeacherSubjectTeacherAndAdmins() throws Exception {
		startChat(parentABearer, "EMPLOYEE", classTeacherA)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.type").value("PARENT_STAFF"))
				.andExpect(jsonPath("$.data.participants[*].name").value(hasItem(org.hamcrest.Matchers.startsWith("Parent A"))));
		startChat(parentABearer, "EMPLOYEE", subjectTeacherA).andExpect(status().isOk());
		startChat(parentABearer, "EMPLOYEE", adminId).andExpect(status().isOk());
	}

	@Test
	void parentCannotStartChatsWithAnyoneElse() throws Exception {
		// Another section's class teacher.
		startChat(parentABearer, "EMPLOYEE", classTeacherB).andExpect(status().isForbidden());
		// A student - even their own child - and another parent.
		startChat(parentABearer, "STUDENT", childA).andExpect(status().isForbidden());
		startChat(parentABearer, "PARENT", parentB.toString()).andExpect(status().isForbidden());
		// Someone who doesn't exist in this school.
		startChat(parentABearer, "EMPLOYEE", UUID.randomUUID().toString()).andExpect(status().isNotFound());
	}

	@Test
	void teacherReachesOnlyParentsOfTheirOwnStudentsWhileAnAdminReachesAnyParent() throws Exception {
		startChat(subjectTeacherABearer, "PARENT", parentA.toString())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.type").value("PARENT_STAFF"));
		startChat(subjectTeacherABearer, "PARENT", parentB.toString()).andExpect(status().isForbidden());
		startChat(adminBearer, "PARENT", parentB.toString()).andExpect(status().isOk());

		String studentBearer = AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "students", childA, "STUDENT");
		startChat(studentBearer, "PARENT", parentA.toString()).andExpect(status().isForbidden());
	}

	@Test
	void theSameParentTeacherPairGetsOneConversationWhoeverStartsIt() throws Exception {
		String fromParent = JsonPath.read(startChat(parentABearer, "EMPLOYEE", subjectTeacherA)
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.id");
		String fromTeacher = JsonPath.read(startChat(subjectTeacherABearer, "PARENT", parentA.toString())
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.id");
		org.junit.jupiter.api.Assertions.assertEquals(fromParent, fromTeacher);
	}

	@Test
	void parentReadsOnlyTheirOwnConversations() throws Exception {
		String ownConversation = JsonPath.read(startChat(parentABearer, "EMPLOYEE", classTeacherA)
				.andReturn().getResponse().getContentAsString(), "$.data.id");
		String staffConversation = JsonPath.read(startChat(subjectTeacherABearer, "EMPLOYEE", classTeacherB)
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(get("/api/v1/chat/conversations/" + ownConversation + "/messages")
						.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "Bearer " + parentABearer))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/chat/conversations/" + ownConversation + "/messages")
						.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "Bearer " + parentBBearer))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/chat/conversations/" + staffConversation + "/messages")
						.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "Bearer " + parentABearer))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/chat/conversations/" + staffConversation + "/attachments/presign")
						.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "Bearer " + parentABearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"fileName\": \"a.pdf\", \"contentType\": \"application/pdf\", \"fileSizeBytes\": 10}"))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/chat/conversations")
						.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "Bearer " + parentABearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].id").value(hasItem(ownConversation)))
				.andExpect(jsonPath("$.data[*].id").value(not(hasItem(staffConversation))));
	}

	@Test
	void contactsListExactlyWhoEachSideMayReach() throws Exception {
		mockMvc.perform(get("/api/v1/chat/contacts")
						.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "Bearer " + parentABearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].ownerId").value(hasItem(classTeacherA)))
				.andExpect(jsonPath("$.data[*].ownerId").value(hasItem(subjectTeacherA)))
				.andExpect(jsonPath("$.data[*].ownerId").value(hasItem(adminId)))
				.andExpect(jsonPath("$.data[*].ownerId").value(not(hasItem(classTeacherB))))
				.andExpect(jsonPath("$.data[?(@.ownerId == '" + classTeacherA + "')].classTeacherOf[0]").value(classA + " - A"))
				.andExpect(jsonPath("$.data[?(@.ownerId == '" + subjectTeacherA + "')].subjects[0]").value("Maths (" + classA + " - A)"))
				.andExpect(jsonPath("$.data[?(@.ownerId == '" + adminId + "')].admin").value(true));

		mockMvc.perform(get("/api/v1/chat/contacts")
						.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "Bearer " + subjectTeacherABearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].ownerId").value(hasItem(parentA.toString())))
				.andExpect(jsonPath("$.data[*].ownerId").value(not(hasItem(parentB.toString()))));
	}

	@Test
	void parentSeesSchoolWideAnnouncementsAndOnlyTheirChildsSectionAndGrade() throws Exception {
		postAnnouncement("{\"scope\": \"CLASS\", \"sectionId\": \"%s\", \"title\": \"For A\", \"body\": \"Hi A\"}".formatted(sectionA));
		postAnnouncement("{\"scope\": \"CLASS\", \"sectionId\": \"%s\", \"title\": \"For B\", \"body\": \"Hi B\"}".formatted(sectionB));

		mockMvc.perform(get("/api/v1/chat/announcements")
						.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "Bearer " + parentABearer))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/chat/announcements?sectionId=" + sectionA + "&className=" + classA)
						.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "Bearer " + parentABearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].title").value(hasItem("For A")))
				.andExpect(jsonPath("$.data[*].title").value(not(hasItem("For B"))));
		mockMvc.perform(get("/api/v1/chat/announcements?sectionId=" + sectionB)
						.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "Bearer " + parentABearer))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/chat/announcements?className=" + classB)
						.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "Bearer " + parentABearer))
				.andExpect(status().isForbidden());
		// Posting stays staff-only.
		mockMvc.perform(post("/api/v1/chat/announcements")
						.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "Bearer " + parentABearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"scope\": \"SCHOOL\", \"title\": \"x\", \"body\": \"y\"}"))
				.andExpect(status().isForbidden());
	}

	private ResultActions startChat(String bearer, String otherType, String otherId) throws Exception {
		return mockMvc.perform(post("/api/v1/chat/conversations")
				.header("X-School-Id", SCHOOL_ID)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"otherPartyOwnerType": "%s", "otherPartyOwnerId": "%s"}
						""".formatted(otherType, otherId)));
	}

	private void postAnnouncement(String json) throws Exception {
		mockMvc.perform(post("/api/v1/chat/announcements")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isOk());
	}

}
