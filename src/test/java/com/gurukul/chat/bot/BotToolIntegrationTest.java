package com.gurukul.chat.bot;

import com.gurukul.attendance.dto.AttendanceDtos.StudentAttendanceHistoryResponse;
import com.gurukul.auth.AuthTestSupport;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.academics.dto.AcademicsDtos.SubjectAssignmentResponse;
import com.gurukul.chat.bot.security.PrincipalContextRunner;
import com.gurukul.chat.bot.tool.MyAttendanceTool;
import com.gurukul.chat.bot.tool.MySubjectsTool;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms the core authorization invariant for bot tools: identity always comes from the
 * caller's AuthPrincipal, never from tool input - two different students each see only their own
 * data.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BotToolIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MyAttendanceTool myAttendanceTool;

	@Autowired
	private MySubjectsTool mySubjectsTool;

	@Autowired
	private PrincipalContextRunner principalContextRunner;

	@Test
	void myAttendanceToolOnlyEverReturnsTheCallersOwnRecord() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String sectionId = createSection(suffix);
		String student1Id = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, "Bot Tool Student One " + suffix);
		String student2Id = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, "Bot Tool Student Two " + suffix);
		String teacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Bot Tool Teacher " + suffix);
		String adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);

		mockMvc.perform(post("/api/v1/class-sections/" + sectionId + "/attendance")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "date": "2026-08-10",
								  "teacherId": "%s",
								  "records": [
								    {"studentId": "%s", "status": "PRESENT"},
								    {"studentId": "%s", "status": "ABSENT"}
								  ]
								}
								""".formatted(teacherId, student1Id, student2Id)))
				.andExpect(status().isOk());

		AuthPrincipal student1Principal = new AuthPrincipal(
				UUID.fromString(student1Id), OwnerType.STUDENT, Role.STUDENT, UUID.fromString(SCHOOL_ID), "student-1");
		AuthPrincipal student2Principal = new AuthPrincipal(
				UUID.fromString(student2Id), OwnerType.STUDENT, Role.STUDENT, UUID.fromString(SCHOOL_ID), "student-2");

		StudentAttendanceHistoryResponse student1Result = (StudentAttendanceHistoryResponse) principalContextRunner
				.runAs(student1Principal, () -> myAttendanceTool.execute(student1Principal, Map.of()));
		StudentAttendanceHistoryResponse student2Result = (StudentAttendanceHistoryResponse) principalContextRunner
				.runAs(student2Principal, () -> myAttendanceTool.execute(student2Principal, Map.of()));

		assertThat(student1Result.getStudentId()).isEqualTo(UUID.fromString(student1Id));
		assertThat(student1Result.getPresentCount()).isEqualTo(1);
		assertThat(student2Result.getStudentId()).isEqualTo(UUID.fromString(student2Id));
		assertThat(student2Result.getAbsentCount()).isEqualTo(1);
	}

	@Test
	void mySubjectsToolReturnsOnlyTheCallersOwnSectionSubjects() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String sectionId = createSection(suffix);
		String studentId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, "Bot Tool Subject Student " + suffix);
		String teacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Bot Tool Subject Teacher " + suffix);

		MvcResult subjectResult = mockMvc.perform(post("/api/v1/subjects")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code": "MATH-%s", "name": "Mathematics"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		String subjectId = JsonPath.read(subjectResult.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(post("/api/v1/class-sections/" + sectionId + "/subjects")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"subjectId": "%s", "teacherId": "%s"}
								""".formatted(subjectId, teacherId)))
				.andExpect(status().isOk());

		AuthPrincipal studentPrincipal = new AuthPrincipal(
				UUID.fromString(studentId), OwnerType.STUDENT, Role.STUDENT, UUID.fromString(SCHOOL_ID), "student");

		@SuppressWarnings("unchecked")
		List<SubjectAssignmentResponse> result = (List<SubjectAssignmentResponse>) principalContextRunner
				.runAs(studentPrincipal, () -> mySubjectsTool.execute(studentPrincipal, Map.of()));

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getSubjectId()).isEqualTo(UUID.fromString(subjectId));
	}

	private String createSection(String suffix) throws Exception {
		MvcResult sectionResult = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 7", "section": "BOT-%s", "academicYear": "2026-27"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(sectionResult.getResponse().getContentAsString(), "$.data.id");
	}

}
