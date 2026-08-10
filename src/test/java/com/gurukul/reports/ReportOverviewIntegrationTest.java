package com.gurukul.reports;

import com.gurukul.auth.AuthTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fee dues (TASK.md Task 11.2) and payroll (Task 11.3) overview reports for the principal. */
@SpringBootTest
@AutoConfigureMockMvc
class ReportOverviewIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void duesReportIncludesUnpaidAssessmentAndItsOverdueSubset() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);

		MvcResult sectionResult = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 10", "section": "DUES-%s", "academicYear": "2026-27"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		String sectionId = JsonPath.read(sectionResult.getResponse().getContentAsString(), "$.data.id");

		MvcResult categoryResult = mockMvc.perform(post("/api/v1/fee-categories")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code": "DUES-TUITION-%s", "name": "Dues Tuition"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		String categoryId = JsonPath.read(categoryResult.getResponse().getContentAsString(), "$.data.id");

		MvcResult structureResult = mockMvc.perform(post("/api/v1/fee-structures")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "classSectionId": "%s",
								  "academicYear": "2026-27",
								  "lines": [{"feeCategoryId": "%s", "amount": 3000.00}]
								}
								""".formatted(sectionId, categoryId)))
				.andExpect(status().isOk())
				.andReturn();
		String structureId = JsonPath.read(structureResult.getResponse().getContentAsString(), "$.data.id");

		AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, "Dues Test Student " + suffix);

		mockMvc.perform(post("/api/v1/fee-structures/" + structureId + "/generate-assessments")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk());

		String body = mockMvc.perform(get("/api/v1/reports/dues").header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		Number totalUnpaid = JsonPath.read(body, "$.data.totalUnpaid");
		assertThat(totalUnpaid.doubleValue()).isGreaterThanOrEqualTo(3000.00);
		List<?> unpaidAssessments = JsonPath.read(body, "$.data.unpaidAssessments");
		assertThat(unpaidAssessments).isNotEmpty();
	}

	@Test
	void payrollOverviewCountsPaidAndPendingEmployeesAcrossRuns() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);

		MvcResult employeeResult = mockMvc.perform(post("/api/v1/employees")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Overview Payroll Teacher %s", "designation": "Teacher", "joinDate": "2024-04-01"}
								""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		String employeeId = JsonPath.read(employeeResult.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(post("/api/v1/salary-structures")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"employeeId": "%s", "basic": 20000.00, "allowances": 2000.00, "deductions": 1000.00, "effectiveFrom": "2024-04-01"}
								""".formatted(employeeId)))
				.andExpect(status().isOk());

		// A month/year far from any real payroll run avoids colliding with the seed data or other
		// tests' runs on this shared school - the overview aggregates across every run for the school.
		int year = 2033;
		int month = 1;

		MvcResult runResult = mockMvc.perform(post("/api/v1/payroll/runs")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"month\": " + month + ", \"year\": " + year + "}"))
				.andExpect(status().isOk())
				.andReturn();
		String runId = JsonPath.read(runResult.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(post("/api/v1/payroll/runs/" + runId + "/process")
						.header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk());

		String beforePayBody = mockMvc.perform(get("/api/v1/reports/payroll/overview").header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		Number pendingBefore = JsonPath.read(beforePayBody, "$.data.pendingEmployeeCount");
		assertThat(pendingBefore.longValue()).isGreaterThanOrEqualTo(1L);

		mockMvc.perform(post("/api/v1/payroll/runs/" + runId + "/pay")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"paymentMethod\": \"BANK_TRANSFER\"}"))
				.andExpect(status().isOk());

		String afterPayBody = mockMvc.perform(get("/api/v1/reports/payroll/overview").header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		Number paidAfter = JsonPath.read(afterPayBody, "$.data.paidEmployeeCount");
		Number paidAmountAfter = JsonPath.read(afterPayBody, "$.data.paidAmount");
		assertThat(paidAfter.longValue()).isGreaterThanOrEqualTo(1L);
		assertThat(paidAmountAfter.doubleValue()).isGreaterThanOrEqualTo(21000.00);
	}

}
