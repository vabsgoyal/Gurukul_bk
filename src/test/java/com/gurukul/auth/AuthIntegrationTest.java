package com.gurukul.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void loginFailsWithWrongPassword() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\": \"admin\", \"password\": \"wrong-password\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void credentialProvisioningRequiresAdmin() throws Exception {
		String employeeId = createEmployee("Some Employee", "Teacher", null);

		mockMvc.perform(post("/api/v1/employees/" + employeeId + "/credentials")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\": \"nope\", \"password\": \"pw123456\", \"role\": \"TEACHER\"}"))
				.andExpect(status().isUnauthorized());

		String teacherUsername = "teacher-" + UUID.randomUUID().toString().substring(0, 8);
		String adminBearer = "Bearer " + AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		mockMvc.perform(post("/api/v1/employees/" + employeeId + "/credentials")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\": \"" + teacherUsername + "\", \"password\": \"pw123456\", \"role\": \"TEACHER\"}"))
				.andExpect(status().isOk());
		String teacherBearer = "Bearer " + AuthTestSupport.login(mockMvc, SCHOOL_ID, teacherUsername, "pw123456");

		String otherEmployeeId = createEmployee("Another Employee", "Teacher", null);
		mockMvc.perform(post("/api/v1/employees/" + otherEmployeeId + "/credentials")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, teacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\": \"blocked\", \"password\": \"pw123456\", \"role\": \"TEACHER\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void onlyTheAssignedClassTeacherCanMarkTheirSectionAttendance() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String sectionId = createSection("Grade 7", "AUTH-OWN-" + suffix);
		String otherSectionId = createSection("Grade 7", "AUTH-OTHER-" + suffix);

		String ownTeacherId = createEmployee("Own Class Teacher", "Teacher", "TEACHING");
		String otherTeacherId = createEmployee("Other Class Teacher", "Teacher", "TEACHING");
		String studentId = enrollStudent(sectionId, "AUTH-" + suffix);

		String adminBearer = "Bearer " + AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		assignClassTeacher(sectionId, ownTeacherId, adminBearer);
		assignClassTeacher(otherSectionId, otherTeacherId, adminBearer);

		String ownTeacherUsername = "own-teacher-" + suffix;
		createCredential("/api/v1/employees/" + ownTeacherId + "/credentials", ownTeacherUsername, "pw123456", "TEACHER", adminBearer);
		String otherTeacherUsername = "other-teacher-" + suffix;
		createCredential("/api/v1/employees/" + otherTeacherId + "/credentials", otherTeacherUsername, "pw123456", "TEACHER", adminBearer);

		String ownTeacherBearer = "Bearer " + AuthTestSupport.login(mockMvc, SCHOOL_ID, ownTeacherUsername, "pw123456");
		String otherTeacherBearer = "Bearer " + AuthTestSupport.login(mockMvc, SCHOOL_ID, otherTeacherUsername, "pw123456");

		String markPayload = """
				{"date": "2026-09-01", "records": [{"studentId": "%s", "status": "PRESENT"}]}
				""".formatted(studentId);

		mockMvc.perform(post("/api/v1/class-sections/" + sectionId + "/attendance")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, otherTeacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(markPayload))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/class-sections/" + sectionId + "/attendance")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, ownTeacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(markPayload))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.entries[?(@.studentId == '" + studentId + "')].status").value("PRESENT"));
	}

	@Test
	void studentCanOnlyViewTheirOwnAttendanceHistory() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String sectionId = createSection("Grade 4", "AUTH-STU-" + suffix);
		String studentAId = enrollStudent(sectionId, "STU-A-" + suffix);
		String studentBId = enrollStudent(sectionId, "STU-B-" + suffix);

		String adminBearer = "Bearer " + AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String studentAUsername = "student-a-" + suffix;
		createCredential("/api/v1/students/" + studentAId + "/credentials", studentAUsername, "pw123456", "STUDENT", adminBearer);
		String studentABearer = "Bearer " + AuthTestSupport.login(mockMvc, SCHOOL_ID, studentAUsername, "pw123456");

		mockMvc.perform(get("/api/v1/students/" + studentAId + "/attendance")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, studentABearer))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/students/" + studentBId + "/attendance")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, studentABearer))
				.andExpect(status().isForbidden());
	}

	@Test
	void teacherCanOnlyViewTheirOwnStaffAttendanceHistory() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String teacherAId = createEmployee("Staff History Teacher A", "Teacher", "TEACHING");
		String teacherBId = createEmployee("Staff History Teacher B", "Teacher", "TEACHING");

		String adminBearer = "Bearer " + AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String teacherAUsername = "history-teacher-a-" + suffix;
		createCredential("/api/v1/employees/" + teacherAId + "/credentials", teacherAUsername, "pw123456", "TEACHER", adminBearer);
		String teacherABearer = "Bearer " + AuthTestSupport.login(mockMvc, SCHOOL_ID, teacherAUsername, "pw123456");

		mockMvc.perform(get("/api/v1/employees/" + teacherAId + "/attendance")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, teacherABearer))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/employees/" + teacherBId + "/attendance")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, teacherABearer))
				.andExpect(status().isForbidden());
	}

	private String createEmployee(String name, String designation, String employeeType) throws Exception {
		String typeField = employeeType != null ? ", \"employeeType\": \"" + employeeType + "\"" : "";
		MvcResult result = mockMvc.perform(post("/api/v1/employees")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"" + name + "\", \"designation\": \"" + designation
								+ "\", \"joinDate\": \"2024-04-01\"" + typeField + "}"))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
	}

	private String createSection(String className, String section) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"className\": \"" + className + "\", \"section\": \"" + section + "\", \"academicYear\": \"2026-27\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
	}

	private String enrollStudent(String sectionId, String rollNumber) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/students")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "rollNumber": "%s",
								  "name": "Auth Test Student",
								  "dob": "2012-05-15",
								  "gender": "MALE",
								  "address": "123 MG Road",
								  "parentName": "Parent Name",
								  "parentContact": "9876543210",
								  "classSectionId": "%s",
								  "admissionDate": "2026-04-01"
								}
								""".formatted(rollNumber, sectionId)))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
	}

	private void assignClassTeacher(String sectionId, String teacherId, String adminBearer) throws Exception {
		mockMvc.perform(patch("/api/v1/class-sections/" + sectionId + "/class-teacher")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"teacherId\": \"" + teacherId + "\"}"))
				.andExpect(status().isOk());
	}

	private void createCredential(String path, String username, String password, String role, String adminBearer) throws Exception {
		mockMvc.perform(post(path)
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\": \"" + username + "\", \"password\": \"" + password + "\", \"role\": \"" + role + "\"}"))
				.andExpect(status().isOk());
	}

}
