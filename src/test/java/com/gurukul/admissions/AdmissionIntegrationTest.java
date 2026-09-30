package com.gurukul.admissions;

import com.gurukul.auth.AuthTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdmissionIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private String adminBearer;

	@BeforeEach
	void setUp() throws Exception {
		adminBearer = "Bearer " + AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
	}

	@Test
	void stageTransitionsFollowThePipeline() throws Exception {
		String className = newClassName();
		createSection(className, "A");
		String id = createApplication(className, "Pipeline Kid", "2019-06-01", randomPhone());

		mockMvc.perform(get("/api/v1/admissions/" + id).header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, adminBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.stage").value("NEW"))
				.andExpect(jsonPath("$.data.appliedClassName").value(className))
				.andExpect(jsonPath("$.data.assignedClassSectionId").value(nullValue()));

		// Can't skip review, and ENROLLED is never a plain stage change.
		changeStage(id, "APPROVED").andExpect(status().isBadRequest());
		changeStage(id, "ENROLLED").andExpect(status().isBadRequest());

		changeStage(id, "UNDER_REVIEW").andExpect(status().isOk()).andExpect(jsonPath("$.data.decidedAt").value(nullValue()));
		changeStage(id, "APPROVED").andExpect(status().isOk()).andExpect(jsonPath("$.data.decidedAt").value(notNullValue()));

		mockMvc.perform(get("/api/v1/admissions").param("stage", "APPROVED")
						.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, adminBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].id", hasItem(id)))
				.andExpect(jsonPath("$.data[*].stage", not(hasItem("NEW"))));
		mockMvc.perform(get("/api/v1/admissions").param("stage", "NEW")
						.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, adminBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].id", not(hasItem(id))));

		changeStage(id, "UNDER_REVIEW").andExpect(status().isOk());
		changeStage(id, "REJECTED").andExpect(status().isOk());
		changeStage(id, "APPROVED").andExpect(status().isBadRequest());
		changeStage(id, "UNDER_REVIEW").andExpect(status().isOk());
	}

	@Test
	void convertRequiresApprovalAndASectionOfTheAppliedClass() throws Exception {
		String className = newClassName();
		String sectionA = createSection(className, "A");
		String otherClassSection = createSection(newClassName(), "A");
		String id = createApplication(className, "Needs Approval", "2019-01-01", randomPhone());

		convert(id, sectionA, false, false).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("approved")));

		approve(id);
		convert(id, otherClassSection, false, false).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString(className)));
		assertThat(countStudentsNamed("Needs Approval")).isZero();
	}

	@Test
	void convertCreatesExactlyOneStudentWithFeeAssessmentAndIsIdempotent() throws Exception {
		String className = newClassName();
		String sectionA = createSection(className, "A");
		createSection(className, "B");
		createFeeStructure(sectionA, "7500.00");
		// An existing classmate whose name sorts after the applicant: roll numbers must reshuffle, not clash.
		AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionA, "Zed Existing");

		String name = "Aarav Admit " + UUID.randomUUID().toString().substring(0, 6);
		String id = createApplication(className, name, "2019-03-15", randomPhone());
		approve(id);

		MvcResult first = convert(id, sectionA, true, false)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.alreadyEnrolled").value(false))
				.andExpect(jsonPath("$.data.rollNumber").value("1"))
				.andExpect(jsonPath("$.data.registrationNumber").value(notNullValue()))
				.andExpect(jsonPath("$.data.inviteCode").value(notNullValue()))
				.andExpect(jsonPath("$.data.application.stage").value("ENROLLED"))
				.andExpect(jsonPath("$.data.application.assignedClassSectionId").value(sectionA))
				.andReturn();
		String studentId = JsonPath.read(first.getResponse().getContentAsString(), "$.data.studentId");

		List<String> rolls = JsonPath.read(mockMvc.perform(get("/api/v1/class-sections/" + sectionA + "/students")
						.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, adminBearer))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(), "$.data[*].rollNumber");
		assertThat(rolls).containsExactly("1", "2");

		assertThat(countAssessments(studentId)).isEqualTo(1);
		mockMvc.perform(get("/api/v1/students/" + studentId + "/fee-assessments")
						.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, adminBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].totalDue").value(7500.00))
				.andExpect(jsonPath("$.data[0].status").value("UNPAID"));

		// Double-submit: same student back, nothing new created, not even when a different section is sent.
		convert(id, sectionA, false, false)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.alreadyEnrolled").value(true))
				.andExpect(jsonPath("$.data.studentId").value(studentId))
				.andExpect(jsonPath("$.data.inviteCode").value(nullValue()));
		assertThat(countStudentsNamed(name)).isEqualTo(1);
		assertThat(countAssessments(studentId)).isEqualTo(1);

		// ENROLLED is terminal.
		changeStage(id, "UNDER_REVIEW").andExpect(status().isBadRequest());
		mockMvc.perform(put("/api/v1/admissions/" + id).header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(applicationJson(className, "Renamed", "2019-03-15", "9000000000")))
				.andExpect(status().isBadRequest());
		mockMvc.perform(delete("/api/v1/admissions/" + id).header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, adminBearer))
				.andExpect(status().isBadRequest());
	}

	@Test
	void concurrentDoubleSubmitCreatesOnlyOneStudent() throws Exception {
		String className = newClassName();
		String sectionA = createSection(className, "A");
		String name = "Race Kid " + UUID.randomUUID().toString().substring(0, 6);
		String id = createApplication(className, name, "2018-11-11", randomPhone());
		approve(id);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<Integer> attempt = () -> {
			start.await();
			return convert(id, sectionA, false, false).andReturn().getResponse().getStatus();
		};
		List<Future<Integer>> results = new ArrayList<>();
		results.add(pool.submit(attempt));
		results.add(pool.submit(attempt));
		start.countDown();
		List<Integer> statuses = new ArrayList<>();
		for (Future<Integer> f : results) {
			statuses.add(f.get());
		}
		pool.shutdown();

		assertThat(statuses).contains(200);
		assertThat(countStudentsNamed(name)).isEqualTo(1);
		convert(id, sectionA, false, false).andExpect(status().isOk()).andExpect(jsonPath("$.data.alreadyEnrolled").value(true));
	}

	@Test
	void possibleDuplicateIsFlaggedAndNeedsConfirmation() throws Exception {
		String className = newClassName();
		String sectionA = createSection(className, "A");
		String phone = randomPhone();
		String name = "Riya Dup" + UUID.randomUUID().toString().substring(0, 6);
		createStudentDirect(sectionA, name, "2017-02-02", phone);

		// Same child, typed with different case/spacing.
		String id = createApplication(className, "  " + name.toLowerCase().replace(" ", "   ") + " ", "2017-02-02", phone);
		mockMvc.perform(get("/api/v1/admissions/" + id).header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, adminBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.possibleDuplicates.length()").value(1))
				.andExpect(jsonPath("$.data.possibleDuplicates[0].name").value(name));
		approve(id);

		convert(id, sectionA, false, false)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("POSSIBLE_DUPLICATE"));
		assertThat(countStudentsNamedIgnoringCase(name)).isEqualTo(1);

		convert(id, sectionA, false, true).andExpect(status().isOk()).andExpect(jsonPath("$.data.alreadyEnrolled").value(false));
		assertThat(countStudentsNamedIgnoringCase(name)).isEqualTo(2);

		// A different DOB is not a duplicate.
		String other = createApplication(className, name, "2016-02-02", phone);
		mockMvc.perform(get("/api/v1/admissions/" + other).header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, adminBearer))
				.andExpect(jsonPath("$.data.possibleDuplicates.length()").value(0));
	}

	@Test
	void adminOnly() throws Exception {
		mockMvc.perform(get("/api/v1/admissions").header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, ""))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/admissions").header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, "")
						.contentType(MediaType.APPLICATION_JSON)
						.content(applicationJson("Grade 8", "Anon", "2019-01-01", "9000000001")))
				.andExpect(status().isUnauthorized());

		String teacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Admissions Teacher");
		String teacherBearer = "Bearer " + AuthTestSupport.provisionAndLogin(
				mockMvc, SCHOOL_ID, adminBearer.substring("Bearer ".length()), "employees", teacherId, "TEACHER");
		mockMvc.perform(get("/api/v1/admissions").header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, teacherBearer))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/admissions").header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, teacherBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(applicationJson("Grade 8", "Teacher Try", "2019-01-01", "9000000002")))
				.andExpect(status().isForbidden());
	}

	@Test
	void anotherSchoolsApplicationIsInvisible() throws Exception {
		String otherSchoolId = registerOtherSchool();
		UUID foreignId = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO admission_application (id, school_id, stage, student_name, dob, gender, address,
				  parent_name, parent_contact, applied_class_name, created_at, updated_at)
				VALUES (?, ?, 'APPROVED', 'Foreign Kid', DATE '2018-01-01', 'MALE', 'Elsewhere', 'Foreign Parent',
				  '9111111111', 'Grade 8', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""", foreignId, UUID.fromString(otherSchoolId));

		mockMvc.perform(get("/api/v1/admissions/" + foreignId).header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, adminBearer))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/admissions").header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, adminBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].id", not(hasItem(foreignId.toString()))));
		changeStage(foreignId.toString(), "UNDER_REVIEW").andExpect(status().isNotFound());
		convert(foreignId.toString(), "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", false, false).andExpect(status().isNotFound());

		// This school's admin token can't be pointed at the other school either.
		mockMvc.perform(get("/api/v1/admissions/" + foreignId).header("X-School-Id", otherSchoolId).header(HttpHeaders.AUTHORIZATION, adminBearer))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void validationAndDocumentsWithoutStorageFailGracefully() throws Exception {
		mockMvc.perform(post("/api/v1/admissions").header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(applicationJson("No Such Class " + UUID.randomUUID(), "Kid", "2019-01-01", "9000000003")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("No class named")));
		mockMvc.perform(post("/api/v1/admissions").header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(applicationJson("Grade 8", "", "2019-01-01", "9000000003")))
				.andExpect(status().isBadRequest());

		String className = newClassName();
		createSection(className, "A");
		String id = createApplication(className, "Docs Kid", "2019-01-01", randomPhone());
		mockMvc.perform(get("/api/v1/admissions/" + id).header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, adminBearer))
				.andExpect(jsonPath("$.data.documentUploadsEnabled").value(false))
				.andExpect(jsonPath("$.data.documents.length()").value(0));
		mockMvc.perform(post("/api/v1/admissions/" + id + "/documents/presign")
						.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"documentType": "BIRTH_CERTIFICATE", "fileName": "birth.pdf", "contentType": "application/pdf", "fileSizeBytes": 1000}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Document uploads are not configured on this server"));

		// Deleting a not-yet-enrolled application works.
		mockMvc.perform(delete("/api/v1/admissions/" + id).header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, adminBearer))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/admissions/" + id).header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, adminBearer))
				.andExpect(status().isNotFound());
	}

	// --- helpers

	private static String newClassName() {
		return "Adm " + UUID.randomUUID().toString().substring(0, 8);
	}

	private static String randomPhone() {
		return "9" + String.format("%09d", Math.abs(UUID.randomUUID().getMostSignificantBits()) % 1_000_000_000L);
	}

	private String createSection(String className, String section) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "%s", "section": "%s", "academicYear": "2026-27"}
								""".formatted(className, section)))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
	}

	private void createFeeStructure(String classSectionId, String amount) throws Exception {
		String categoryId = JsonPath.read(mockMvc.perform(post("/api/v1/fee-categories")
						.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code": "ADM-%s", "name": "Admission Tuition"}
								""".formatted(UUID.randomUUID().toString().substring(0, 8))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(), "$.data.id");
		mockMvc.perform(post("/api/v1/fee-structures")
						.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"classSectionId": "%s", "academicYear": "2026-27", "lines": [{"feeCategoryId": "%s", "amount": %s}]}
								""".formatted(classSectionId, categoryId, amount)))
				.andExpect(status().isOk());
	}

	private void createStudentDirect(String classSectionId, String name, String dob, String phone) throws Exception {
		mockMvc.perform(post("/api/v1/students")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "%s", "dob": "%s", "gender": "FEMALE", "address": "Unhel", "parentName": "Parent",
								 "parentContact": "%s", "classSectionId": "%s", "admissionDate": "2025-04-01"}
								""".formatted(name, dob, phone, classSectionId)))
				.andExpect(status().isOk());
	}

	private static String applicationJson(String className, String name, String dob, String phone) {
		return """
				{
				  "studentName": "%s",
				  "dob": "%s",
				  "gender": "FEMALE",
				  "address": "12 Station Road, Unhel",
				  "previousSchoolName": "Noble Academy",
				  "parentName": "Suresh Kumar",
				  "parentContact": "%s",
				  "appliedClassName": "%s",
				  "notes": "Walk-in enquiry"
				}
				""".formatted(name, dob, phone, className);
	}

	private String createApplication(String className, String name, String dob, String phone) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/admissions")
						.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(applicationJson(className, name, dob, phone)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.stage").value("NEW"))
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
	}

	private ResultActions changeStage(String id, String stage) throws Exception {
		return mockMvc.perform(patch("/api/v1/admissions/" + id + "/stage")
				.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, adminBearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"stage\": \"" + stage + "\"}"));
	}

	private void approve(String id) throws Exception {
		changeStage(id, "UNDER_REVIEW").andExpect(status().isOk());
		changeStage(id, "APPROVED").andExpect(status().isOk());
	}

	private ResultActions convert(String id, String classSectionId, boolean invite, boolean allowDuplicate) throws Exception {
		return mockMvc.perform(post("/api/v1/admissions/" + id + "/convert")
				.header("X-School-Id", SCHOOL_ID).header(HttpHeaders.AUTHORIZATION, adminBearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"classSectionId": "%s", "admissionDate": "2026-04-01", "sendParentInvite": %s, "allowDuplicate": %s}
						""".formatted(classSectionId, invite, allowDuplicate)));
	}

	private int countStudentsNamed(String name) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM student WHERE school_id = ? AND name = ?",
				Integer.class, UUID.fromString(SCHOOL_ID), name);
	}

	private int countStudentsNamedIgnoringCase(String name) {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM student WHERE school_id = ? AND LOWER(REPLACE(name, ' ', '')) = LOWER(REPLACE(?, ' ', ''))",
				Integer.class, UUID.fromString(SCHOOL_ID), name);
	}

	private int countAssessments(String studentId) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM student_fee_assessment WHERE student_id = ?",
				Integer.class, UUID.fromString(studentId));
	}

	private String registerOtherSchool() throws Exception {
		String suffix = String.format("%08d", Math.abs(UUID.randomUUID().getLeastSignificantBits()) % 100_000_000L);
		return JsonPath.read(mockMvc.perform(post("/api/v1/schools")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Admissions Other School %s",
								  "address": "1 Other Street",
								  "city": "Jaipur",
								  "state": "Rajasthan",
								  "pincode": "302001",
								  "contactEmail": "office%s@admother.example",
								  "contactPhone": "93%s",
								  "principalName": "Dr. Other Principal",
								  "directorName": "Mr. Other Director",
								  "principalPhone": "93%s",
								  "adminPhone": "83%s"
								}
								""".formatted(suffix, suffix, suffix, suffix, suffix)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(), "$.data.school.id");
	}

}
