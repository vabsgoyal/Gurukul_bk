package com.gurukul.parents;

import com.gurukul.academics.entity.SectionSubjectTeacher;
import com.gurukul.academics.entity.Subject;
import com.gurukul.academics.repository.SectionSubjectTeacherRepository;
import com.gurukul.academics.repository.SubjectRepository;
import com.gurukul.auth.entity.Credential;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.JwtService;
import com.gurukul.employees.repository.EmployeeRepository;
import com.gurukul.parents.entity.Parent;
import com.gurukul.parents.entity.ParentStudentLink;
import com.gurukul.parents.repository.ParentRepository;
import com.gurukul.parents.repository.ParentStudentLinkRepository;
import com.gurukul.students.repository.ClassSectionRepository;
import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parents have no admin-provisioned credential path (they self-register and get approved), so tests
 * create the Parent + link rows directly and mint the parent's JWT with the real JwtService - the
 * token is exactly what a logged-in parent's app would send.
 */
public final class ParentTestSupport {

	private ParentTestSupport() {
	}

	public static UUID createParent(ParentRepository parents, ParentStudentLinkRepository links, String schoolId,
			String name, String... childIds) {
		Parent parent = new Parent();
		parent.setSchoolId(UUID.fromString(schoolId));
		parent.setName(name);
		parent.setPhone("8" + String.format("%09d", Math.abs(name.hashCode()) % 1_000_000_000));
		UUID parentId = parents.save(parent).getId();
		for (String childId : childIds) {
			ParentStudentLink link = new ParentStudentLink();
			link.setSchoolId(UUID.fromString(schoolId));
			link.setParentId(parentId);
			link.setStudentId(UUID.fromString(childId));
			links.save(link);
		}
		return parentId;
	}

	public static String parentBearer(JwtService jwtService, String schoolId, UUID parentId) {
		Credential credential = new Credential();
		credential.setSchoolId(UUID.fromString(schoolId));
		credential.setOwnerType(OwnerType.PARENT);
		credential.setOwnerId(parentId);
		credential.setUsername("parent-" + parentId);
		credential.setRole(Role.PARENT);
		return jwtService.generateToken(credential);
	}

	public static String createSection(MockMvc mockMvc, String schoolId, String className, String section) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", schoolId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "%s", "section": "%s", "academicYear": "2026-27"}
								""".formatted(className, section)))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
	}

	public static void assignClassTeacher(MockMvc mockMvc, String schoolId, String adminBearer, String sectionId,
			String teacherId) throws Exception {
		mockMvc.perform(patch("/api/v1/class-sections/" + sectionId + "/class-teacher")
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"teacherId": "%s"}
								""".formatted(teacherId)))
				.andExpect(status().isOk());
	}

	public static void assignSubjectTeacher(SubjectRepository subjects, SectionSubjectTeacherRepository assignments,
			ClassSectionRepository sections, EmployeeRepository employees, String schoolId, String sectionId,
			String teacherId, String subjectName) {
		Subject subject = new Subject();
		subject.setSchoolId(UUID.fromString(schoolId));
		subject.setCode("S-" + UUID.randomUUID().toString().substring(0, 8));
		subject.setName(subjectName);
		subject = subjects.save(subject);
		SectionSubjectTeacher assignment = new SectionSubjectTeacher();
		assignment.setSchoolId(UUID.fromString(schoolId));
		assignment.setSection(sections.findById(UUID.fromString(sectionId)).orElseThrow());
		assignment.setSubject(subject);
		assignment.setTeacher(employees.findById(UUID.fromString(teacherId)).orElseThrow());
		assignments.save(assignment);
	}

}
