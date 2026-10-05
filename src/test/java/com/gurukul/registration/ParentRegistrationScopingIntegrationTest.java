package com.gurukul.registration;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.repository.CredentialRepository;
import com.gurukul.auth.security.JwtService;
import com.gurukul.parents.ParentTestSupport;
import com.gurukul.parents.repository.ParentRepository;
import com.gurukul.parents.repository.ParentStudentLinkRepository;
import com.gurukul.schools.SchoolTestSupport;
import com.gurukul.schools.SchoolTestSupport.RegisteredSchool;
import com.gurukul.students.repository.StudentRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Parent self-registration approvals and sibling links stay inside the parent's own school and family. */
@SpringBootTest
@AutoConfigureMockMvc
class ParentRegistrationScopingIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private StudentRepository studentRepository;

	@Autowired
	private CredentialRepository credentialRepository;

	@Autowired
	private ParentRepository parentRepository;

	@Autowired
	private ParentStudentLinkRepository parentStudentLinkRepository;

	@Autowired
	private JwtService jwtService;

	@Test
	void anotherSchoolsAdminCannotApproveOrRejectAPendingParent() throws Exception {
		RegisteredSchool school = SchoolTestSupport.register(mockMvc, "Approval Home School");
		RegisteredSchool other = SchoolTestSupport.register(mockMvc, "Approval Other School");
		String parentPhone = "7" + SchoolTestSupport.randomDigits(9);
		String sectionId = ParentTestSupport.createSection(mockMvc, school.id(), "6", "A");
		String registrationNumber = registrationNumberOf(createStudent(school.id(), sectionId, "Approval Child", parentPhone));

		String registered = mockMvc.perform(post("/api/v1/register/parent")
						.header("X-School-Id", school.id())
						.header(HttpHeaders.AUTHORIZATION, "")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"studentRegistrationNumber": "%s", "parentContact": "%s",
								 "username": "parent-%s", "password": "Password@123"}
								""".formatted(registrationNumber, parentPhone, parentPhone)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String parentId = JsonPath.read(registered, "$.data.entityId");

		decide(other, "approve", parentId).andExpect(status().isNotFound());
		decide(other, "reject", parentId).andExpect(status().isNotFound());
		assertThat(credentialRepository.findByOwnerTypeAndOwnerId(OwnerType.PARENT, UUID.fromString(parentId))
				.orElseThrow().isEnabled()).isFalse();

		decide(school, "approve", parentId).andExpect(status().isOk());
		assertThat(credentialRepository.findByOwnerTypeAndOwnerId(OwnerType.PARENT, UUID.fromString(parentId))
				.orElseThrow().isEnabled()).isTrue();
	}

	@Test
	void linkingAChildRequiresTheChildsParentContactToBeTheParentsPhone() throws Exception {
		RegisteredSchool school = SchoolTestSupport.register(mockMvc, "Sibling Link School");
		String sectionId = ParentTestSupport.createSection(mockMvc, school.id(), "7", "A");
		UUID parentId = ParentTestSupport.createParent(parentRepository, parentStudentLinkRepository, school.id(),
				"Sibling Parent " + SchoolTestSupport.randomDigits(6));
		String parentPhone = parentRepository.findById(parentId).orElseThrow().getPhone();
		String parentBearer = ParentTestSupport.parentBearer(jwtService, school.id(), parentId);

		String strangerChild = registrationNumberOf(
				createStudent(school.id(), sectionId, "Someone Elses Child", "7" + SchoolTestSupport.randomDigits(9)));
		link(school.id(), parentBearer, strangerChild)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("doesn't match your account's phone number")));

		String sibling = registrationNumberOf(createStudent(school.id(), sectionId, "Real Sibling", parentPhone));
		link(school.id(), parentBearer, sibling).andExpect(status().isOk());

		assertThat(parentStudentLinkRepository.existsByParentIdAndStudentId(parentId,
				studentRepository.findBySchoolIdAndRegistrationNumber(UUID.fromString(school.id()), strangerChild)
						.orElseThrow().getId())).isFalse();
	}

	@Test
	void repeatedMismatchedLinkAttemptsLockTheRegistrationNumber() throws Exception {
		RegisteredSchool school = SchoolTestSupport.register(mockMvc, "Sibling Lock School");
		String sectionId = ParentTestSupport.createSection(mockMvc, school.id(), "8", "A");
		UUID parentId = ParentTestSupport.createParent(parentRepository, parentStudentLinkRepository, school.id(),
				"Guessing Parent " + SchoolTestSupport.randomDigits(6));
		String parentBearer = ParentTestSupport.parentBearer(jwtService, school.id(), parentId);
		String target = registrationNumberOf(
				createStudent(school.id(), sectionId, "Targeted Child", "7" + SchoolTestSupport.randomDigits(9)));

		for (int i = 0; i < 5; i++) {
			link(school.id(), parentBearer, target).andExpect(status().isBadRequest());
		}
		link(school.id(), parentBearer, target)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Too many failed attempts - try again later"));
	}

	private ResultActions decide(RegisteredSchool as, String decision, String parentId) throws Exception {
		return mockMvc.perform(post("/api/v1/registrations/PARENT_REGISTRATION/" + parentId + "/" + decision)
				.header("X-School-Id", as.id())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + as.adminToken()));
	}

	private ResultActions link(String schoolId, String parentBearer, String registrationNumber) throws Exception {
		return mockMvc.perform(post("/api/v1/parents/me/children")
				.header("X-School-Id", schoolId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + parentBearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"studentRegistrationNumber": "%s"}
						""".formatted(registrationNumber)));
	}

	private String createStudent(String schoolId, String sectionId, String name, String parentContact) throws Exception {
		String json = mockMvc.perform(post("/api/v1/students")
						.header("X-School-Id", schoolId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"rollNumber": "R-%s", "name": "%s", "dob": "2013-05-15", "gender": "FEMALE",
								 "address": "1 Test Street", "parentName": "Parent of %s", "parentContact": "%s",
								 "classSectionId": "%s", "admissionDate": "2026-04-01"}
								""".formatted(SchoolTestSupport.randomDigits(6), name, name, parentContact, sectionId)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.data.id");
	}

	private String registrationNumberOf(String studentId) {
		return studentRepository.findById(UUID.fromString(studentId)).orElseThrow().getRegistrationNumber();
	}

}
