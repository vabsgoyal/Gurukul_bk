package com.gurukul.idcards;

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
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ID cards end to end (no S3 bucket configured): who may edit whose details, who may see which card,
 * QR verification, and the admin sheets. Tokens are minted directly with JwtService for the
 * student/parent/teacher principals, so each test controls exactly who is calling.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IdCardIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final UUID SCHOOL = UUID.fromString(SCHOOL_ID);

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JwtService jwtService;
	@Autowired
	private ParentRepository parentRepository;
	@Autowired
	private ParentStudentLinkRepository linkRepository;

	// ---------------------------------------------------------------- students

	@Test
	void aStudentEditsOnlyTheirOwnDetailsAndTheMissingListShrinks() throws Exception {
		String section = createSection();
		String studentA = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, section, "Card Student A");
		String studentB = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, section, "Card Student B");
		String tokenA = token(OwnerType.STUDENT, studentA, Role.STUDENT);

		mockMvc.perform(as(get("/api/v1/id-cards/students/" + studentA), tokenA))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("Card Student A"))
				.andExpect(jsonPath("$.data.canEdit").value(true))
				.andExpect(jsonPath("$.data.missing[0]").value("PHOTO"))
				.andExpect(jsonPath("$.data.missing[1]").value("BLOOD_GROUP"))
				.andExpect(jsonPath("$.data.qrImage").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")));

		mockMvc.perform(as(put("/api/v1/id-cards/students/" + studentA + "/profile"), tokenA)
						.content("{\"bloodGroup\": \"b +ve\", \"emergencyContactName\": \"Uncle\", \"emergencyPhone\": \"+91 98765 43210\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.bloodGroup").value("B+"))
				.andExpect(jsonPath("$.data.emergencyPhone").value("+919876543210"))
				.andExpect(jsonPath("$.data.cardPhone").value("+919876543210"))
				.andExpect(jsonPath("$.data.missing.length()").value(1));

		// Someone else's card: neither editable nor visible.
		mockMvc.perform(as(put("/api/v1/id-cards/students/" + studentB + "/profile"), tokenA).content("{\"bloodGroup\": \"O+\"}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(get("/api/v1/id-cards/students/" + studentB), tokenA)).andExpect(status().isForbidden());
		mockMvc.perform(as(get("/api/v1/id-cards/students/" + studentB + "/card.pdf"), tokenA)).andExpect(status().isForbidden());

		// Validation.
		mockMvc.perform(as(put("/api/v1/id-cards/students/" + studentA + "/profile"), tokenA).content("{\"bloodGroup\": \"C+\"}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(as(put("/api/v1/id-cards/students/" + studentA + "/profile"), tokenA).content("{\"emergencyPhone\": \"12ab\"}"))
				.andExpect(status().isBadRequest());

		// /me is the student's own card.
		mockMvc.perform(as(get("/api/v1/id-cards/me"), tokenA))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].ownerId").value(studentA));
	}

	@Test
	void anAdminSeesAnyStudentCardButCannotEditItAndATeacherSeesNeither() throws Exception {
		String section = createSection();
		String student = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, section, "Admin View Student");
		String admin = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String teacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Some Teacher");
		String teacher = token(OwnerType.EMPLOYEE, teacherId, Role.TEACHER);

		mockMvc.perform(as(get("/api/v1/id-cards/students/" + student), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.canEdit").value(false));
		mockMvc.perform(as(get("/api/v1/id-cards/students/" + student + "/card.pdf"), admin)).andExpect(status().isOk());
		mockMvc.perform(as(put("/api/v1/id-cards/students/" + student + "/profile"), admin).content("{\"bloodGroup\": \"O+\"}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(post("/api/v1/id-cards/students/" + student + "/photo/presign"), admin)
						.content("{\"contentType\": \"image/jpeg\", \"fileSizeBytes\": 1000}"))
				.andExpect(status().isForbidden());

		mockMvc.perform(as(get("/api/v1/id-cards/students/" + student), teacher)).andExpect(status().isForbidden());
		mockMvc.perform(as(put("/api/v1/id-cards/students/" + student + "/profile"), teacher).content("{\"bloodGroup\": \"O+\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void aLinkedParentEditsTheChildsDetailsAndAnUnlinkedParentCannot() throws Exception {
		String section = createSection();
		String child = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, section, "Parent Child");
		String linkedParent = parent(child);
		String otherParent = parent(null);

		mockMvc.perform(as(put("/api/v1/id-cards/students/" + child + "/profile"), linkedParent).content("{\"bloodGroup\": \"AB-\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.bloodGroup").value("AB-"))
				.andExpect(jsonPath("$.data.canEdit").value(true));
		mockMvc.perform(as(get("/api/v1/id-cards/me"), linkedParent))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].bloodGroup").value("AB-"));
		mockMvc.perform(as(get("/api/v1/id-cards/students/" + child + "/card.pdf"), linkedParent)).andExpect(status().isOk());

		mockMvc.perform(as(put("/api/v1/id-cards/students/" + child + "/profile"), otherParent).content("{\"bloodGroup\": \"O+\"}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(get("/api/v1/id-cards/students/" + child), otherParent)).andExpect(status().isForbidden());
		mockMvc.perform(as(get("/api/v1/id-cards/me"), otherParent))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(0));
	}

	// ---------------------------------------------------------------- staff

	@Test
	void staffEditOnlyTheirOwnDetailsAndAdminsCanOnlyView() throws Exception {
		String e1 = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Staff One");
		String e2 = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Staff Two");
		String t1 = token(OwnerType.EMPLOYEE, e1, Role.TEACHER);
		String admin = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);

		mockMvc.perform(as(get("/api/v1/id-cards/employees/" + e1), t1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.missing.length()").value(3))
				.andExpect(jsonPath("$.data.missing[2]").value("EMERGENCY_CONTACT"));
		mockMvc.perform(as(put("/api/v1/id-cards/employees/" + e1 + "/profile"), t1)
						.content("{\"bloodGroup\": \"A+\", \"emergencyPhone\": \"9812345678\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.missing.length()").value(1));

		mockMvc.perform(as(put("/api/v1/id-cards/employees/" + e2 + "/profile"), t1).content("{\"bloodGroup\": \"A+\"}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(get("/api/v1/id-cards/employees/" + e2), t1)).andExpect(status().isForbidden());
		mockMvc.perform(as(get("/api/v1/id-cards/employees/" + e2 + "/card.pdf"), t1)).andExpect(status().isForbidden());

		mockMvc.perform(as(get("/api/v1/id-cards/employees/" + e2), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.canEdit").value(false));
		mockMvc.perform(as(put("/api/v1/id-cards/employees/" + e2 + "/profile"), admin).content("{\"bloodGroup\": \"A+\"}"))
				.andExpect(status().isForbidden());

		// A student can never reach staff card endpoints.
		String section = createSection();
		String student = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, section, "Nosy Student");
		mockMvc.perform(as(get("/api/v1/id-cards/employees/" + e1), token(OwnerType.STUDENT, student, Role.STUDENT)))
				.andExpect(status().isForbidden());

		byte[] pdf = mockMvc.perform(as(get("/api/v1/id-cards/employees/" + e1 + "/card.pdf"), t1))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("id-card-staff-one.pdf")))
				.andReturn().getResponse().getContentAsByteArray();
		try (PDDocument document = Loader.loadPDF(pdf)) {
			assertThat(document.getNumberOfPages()).isEqualTo(1);
			assertThat(document.getPage(0).getMediaBox().getWidth()).isBetween(241f, 244f); // 85.6 mm
			assertThat(new PDFTextStripper().getText(document)).contains("Staff One", "STAFF", "A+", "9812345678");
		}
	}

	// ---------------------------------------------------------------- verify + cross-school

	@Test
	void staffOfTheSameSchoolVerifyAQrAndNobodyElseCan() throws Exception {
		String section = createSection();
		String student = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, section, "Verify Me");
		String studentToken = token(OwnerType.STUDENT, student, Role.STUDENT);
		String code = JsonPath.read(mockMvc.perform(as(get("/api/v1/id-cards/students/" + student), studentToken))
				.andReturn().getResponse().getContentAsString(), "$.data.qrCode");
		String teacher = token(OwnerType.EMPLOYEE, AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Gate Teacher"), Role.TEACHER);

		mockMvc.perform(as(get("/api/v1/id-cards/verify").param("code", code), teacher))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("Verify Me"))
				.andExpect(jsonPath("$.data.ownerType").value("STUDENT"))
				.andExpect(jsonPath("$.data.active").value(true))
				// Identification only: no personal details beyond name/class/photo.
				.andExpect(jsonPath("$.data.bloodGroup").doesNotExist())
				.andExpect(jsonPath("$.data.cardPhone").doesNotExist());

		String tampered = code.substring(0, code.length() - 2) + (code.endsWith("AA") ? "BB" : "AA");
		mockMvc.perform(as(get("/api/v1/id-cards/verify").param("code", tampered), teacher)).andExpect(status().isNotFound());
		mockMvc.perform(as(get("/api/v1/id-cards/verify").param("code", code), studentToken)).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/id-cards/verify").param("code", code).header("X-School-Id", SCHOOL_ID).header("Authorization", ""))
				.andExpect(status().isUnauthorized());

		// Another school's admin: the code doesn't verify, and the card itself isn't reachable.
		String otherSchool = registerSchool("IdCardOther");
		String otherSchoolId = JsonPath.read(otherSchool, "$.data.school.id");
		String otherAdmin = JsonPath.read(otherSchool, "$.data.principal.token");
		mockMvc.perform(get("/api/v1/id-cards/verify").param("code", code)
						.header("X-School-Id", otherSchoolId).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherAdmin))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/id-cards/students/" + student)
						.header("X-School-Id", otherSchoolId).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherAdmin))
				.andExpect(status().isNotFound());
		// ...and its token is useless against this school's header.
		mockMvc.perform(get("/api/v1/id-cards/students/" + student)
						.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherAdmin))
				.andExpect(status().isUnauthorized());
	}

	// ---------------------------------------------------------------- sheets, storage, unmatched paths

	@Test
	void adminPrintsSectionAndStaffSheetsTenCardsPerPage() throws Exception {
		String section = createSection();
		for (int i = 1; i <= 11; i++) { // odd: the last row has one card
			AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, section, "Sheet Student " + i);
		}
		String admin = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		byte[] pdf = mockMvc.perform(as(get("/api/v1/id-cards/class-sections/" + section + "/sheet.pdf"), admin))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsByteArray();
		try (PDDocument document = Loader.loadPDF(pdf)) {
			assertThat(document.getNumberOfPages()).isEqualTo(2);
			assertThat(document.getPage(0).getMediaBox().getHeight()).isBetween(841f, 843f); // A4
			assertThat(new PDFTextStripper().getText(document)).contains("Sheet Student 1", "Sheet Student 11");
		}

		AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Sheet Staff");
		byte[] staff = mockMvc.perform(as(get("/api/v1/id-cards/staff/sheet.pdf"), admin))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsByteArray();
		try (PDDocument document = Loader.loadPDF(staff)) {
			assertThat(new PDFTextStripper().getText(document)).contains("Sheet Staff", "STAFF");
		}

		String teacher = token(OwnerType.EMPLOYEE, AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Not Admin"), Role.TEACHER);
		mockMvc.perform(as(get("/api/v1/id-cards/class-sections/" + section + "/sheet.pdf"), teacher)).andExpect(status().isForbidden());
		mockMvc.perform(as(get("/api/v1/id-cards/staff/sheet.pdf"), teacher)).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/id-cards/staff/sheet.pdf").header("X-School-Id", SCHOOL_ID).header("Authorization", "")).andExpect(status().isUnauthorized());
	}

	@Test
	void photoUploadFailsCleanlyWithoutABucketAndUnknownPathsAreDenied() throws Exception {
		String section = createSection();
		String student = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, section, "No Bucket Student");
		String token = token(OwnerType.STUDENT, student, Role.STUDENT);
		mockMvc.perform(as(post("/api/v1/id-cards/students/" + student + "/photo/presign"), token)
						.content("{\"contentType\": \"image/jpeg\", \"fileSizeBytes\": 1000}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Profile photo upload is not configured on this server"));
		// Removing a photo that was never set is harmless.
		mockMvc.perform(as(delete("/api/v1/id-cards/students/" + student + "/photo"), token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.hasPhoto").value(false));
		// The card still downloads, with a placeholder.
		mockMvc.perform(as(get("/api/v1/id-cards/students/" + student + "/card.pdf"), token)).andExpect(status().isOk());

		mockMvc.perform(as(get("/api/v1/id-cards/students"), token)).andExpect(status().isForbidden());
	}

	// ---------------------------------------------------------------- helpers

	private String token(OwnerType ownerType, String ownerId, Role role) {
		Credential credential = new Credential();
		credential.setSchoolId(SCHOOL);
		credential.setOwnerType(ownerType);
		credential.setOwnerId(UUID.fromString(ownerId));
		credential.setRole(role);
		credential.setUsername("idcard-" + UUID.randomUUID().toString().substring(0, 8));
		return jwtService.generateToken(credential);
	}

	/** A parent linked to {@code childId} (or to nobody, if null), and a token for them. */
	private String parent(String childId) {
		Parent parent = new Parent();
		parent.setSchoolId(SCHOOL);
		parent.setName("Test Parent");
		parent = parentRepository.save(parent);
		if (childId != null) {
			ParentStudentLink link = new ParentStudentLink();
			link.setSchoolId(SCHOOL);
			link.setParentId(parent.getId());
			link.setStudentId(UUID.fromString(childId));
			linkRepository.saveAll(List.of(link));
		}
		return token(OwnerType.PARENT, parent.getId().toString(), Role.PARENT);
	}

	private String createSection() throws Exception {
		String response = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 6", "section": "ID-%s", "academicYear": "2026-27"}
								""".formatted(UUID.randomUUID().toString().substring(0, 8))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.data.id");
	}

	private String registerSchool(String namePrefix) throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 6);
		return mockMvc.perform(post("/api/v1/schools")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "%s %s School",
								  "address": "1 Test Street",
								  "city": "Jaipur",
								  "state": "Rajasthan",
								  "pincode": "302001",
								  "contactEmail": "office@%s.example",
								  "contactPhone": "9333333333",
								  "principalName": "Dr. Other Principal",
								  "directorName": "Mr. Other Director",
								  "principalPhone": "9333333333",
								  "adminPhone": "8333333333"
								}
								""".formatted(namePrefix, suffix, suffix)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) {
		return request
				.header("X-School-Id", SCHOOL_ID)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON);
	}

}
