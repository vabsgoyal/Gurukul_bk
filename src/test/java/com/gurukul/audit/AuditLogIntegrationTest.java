package com.gurukul.audit;

import com.gurukul.auth.AuthTestSupport;
import com.gurukul.schools.entity.School;
import com.gurukul.schools.repository.SchoolRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuditLogIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private SchoolRepository schoolRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private String adminBearer;

	@BeforeEach
	void setUp() throws Exception {
		adminBearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
	}

	@Test
	void createAndUpdateAreLoggedWithActorAndFieldDiff() throws Exception {
		String employeeId = JsonPath.read(mockMvc.perform(post("/api/v1/employees")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Audit Before", "designation": "Teacher", "joinDate": "2024-04-01"}
								"""))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(put("/api/v1/employees/" + employeeId)
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Audit After", "designation": "Teacher", "joinDate": "2024-04-01"}
								"""))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/audit-logs")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.param("entityType", "Employee")
						.param("entityId", employeeId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				// newest first
				.andExpect(jsonPath("$.data[0].action").value("UPDATE"))
				.andExpect(jsonPath("$.data[0].changes.name.old").value("Audit Before"))
				.andExpect(jsonPath("$.data[0].changes.name.new").value("Audit After"))
				// only the changed field is in an UPDATE diff
				.andExpect(jsonPath("$.data[0].changes.designation").doesNotExist())
				.andExpect(jsonPath("$.data[0].actorUsername").value(AuthTestSupport.DEV_ADMIN_USERNAME))
				.andExpect(jsonPath("$.data[0].actorRole").value("ADMIN"))
				.andExpect(jsonPath("$.data[1].action").value("CREATE"))
				.andExpect(jsonPath("$.data[1].changes.name.new").value("Audit Before"))
				.andExpect(jsonPath("$.data[1].changes.name.old").doesNotExist());

		mockMvc.perform(get("/api/v1/audit-logs/entity-types")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasItem("Employee")));
	}

	@Test
	void credentialPasswordHashIsRedacted() throws Exception {
		String employeeId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Audit Redaction");
		AuthTestSupport.provisionAndLogin(mockMvc, SCHOOL_ID, adminBearer, "employees", employeeId, "TEACHER");

		List<String> changes = jdbcTemplate.queryForList(
				"SELECT changes FROM audit_log WHERE entity_type = 'Credential' AND changes LIKE ?",
				String.class, "%" + employeeId + "%");
		assertThat(changes).hasSize(1);
		assertThat(changes.getFirst()).contains("\"passwordHash\":{\"new\":\"[redacted]\"}");
		assertThat(changes.getFirst()).doesNotContain("$2a$").doesNotContain("Password@123");
	}

	@Test
	void writesWithNoAuthenticatedUserAreAttributedToSystem() throws Exception {
		String employeeId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Audit System Actor");

		String actor = jdbcTemplate.queryForObject(
				"SELECT actor_username FROM audit_log WHERE entity_type = 'Employee' AND entity_id = ? AND action = 'CREATE'",
				String.class, employeeId);
		assertThat(actor).isEqualTo("SYSTEM");
	}

	@Test
	void rolledBackChangeLeavesNoAuditRow() {
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		UUID[] id = new UUID[1];
		tx.executeWithoutResult(status -> {
			id[0] = schoolRepository.saveAndFlush(newSchool("Audit Rollback School")).getId();
			status.setRollbackOnly();
		});

		Long rows = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM audit_log WHERE entity_id = ?", Long.class, id[0].toString());
		assertThat(rows).isZero();
	}

	@Test
	void nonAdminCannotReadTheLog() throws Exception {
		String employeeId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Audit Teacher Reader");
		String teacherBearer = AuthTestSupport.provisionAndLogin(
				mockMvc, SCHOOL_ID, adminBearer, "employees", employeeId, "TEACHER");

		mockMvc.perform(get("/api/v1/audit-logs")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherBearer))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminOnlySeesOwnSchoolsRows() throws Exception {
		// Registered through the real endpoint, not saved bare: startup seeders in later test contexts
		// iterate every school and expect each one to have its registration-time principal/admin.
		String otherSchoolId = JsonPath.read(mockMvc.perform(post("/api/v1/schools")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Audit Other School",
								  "address": "1 Audit Street",
								  "city": "Jaipur",
								  "state": "Rajasthan",
								  "pincode": "302001",
								  "contactEmail": "office@auditother.example",
								  "contactPhone": "9334567890",
								  "principalName": "Dr. Audit Principal",
								  "directorName": "Mr. Audit Director",
								  "principalPhone": "9334567890",
								  "adminPhone": "8334567890"
								}
								"""))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(), "$.data.school.id");
		Long otherSchoolRows = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM audit_log WHERE school_id = ?", Long.class, UUID.fromString(otherSchoolId));
		assertThat(otherSchoolRows).isPositive();

		mockMvc.perform(get("/api/v1/audit-logs")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.param("entityType", "School")
						.param("entityId", otherSchoolId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(0));
	}

	private School newSchool(String name) {
		School school = new School();
		school.setName(name);
		school.setAddress("1 Test Road");
		school.setCity("Jaipur");
		school.setState("Rajasthan");
		school.setPincode("302001");
		school.setContactEmail("test@example.com");
		school.setContactPhone("9999999999");
		school.setPrincipalName("Principal");
		school.setDirectorName("Director");
		return school;
	}

}
