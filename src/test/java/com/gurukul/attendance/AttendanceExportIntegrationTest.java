package com.gurukul.attendance;

import com.gurukul.auth.AuthTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AttendanceExportIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final String CLASS_SECTION_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
	private static final String DATE = "2026-03-17";
	private static final String TIMESTAMP_PATTERN = "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}";

	@Autowired
	private MockMvc mockMvc;

	private String adminBearer;

	@BeforeEach
	void setUp() throws Exception {
		adminBearer = "Bearer " + AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
	}

	@Test
	void studentExportContainsMarkedRecordWithTimestamps() throws Exception {
		String studentName = "Export Student " + UUID.randomUUID().toString().substring(0, 6);
		String studentId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, CLASS_SECTION_B, studentName);
		String teacherId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Export Teacher");

		mockMvc.perform(post("/api/v1/class-sections/" + CLASS_SECTION_B + "/attendance")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"date": "%s", "teacherId": "%s",
								 "records": [{"studentId": "%s", "status": "LATE", "remarks": "Bus delay"}]}
								""".formatted(DATE, teacherId, studentId)))
				.andExpect(status().isOk());

		MvcResult result = mockMvc.perform(get("/api/v1/attendance/export")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, adminBearer)
						.param("type", "STUDENT")
						.param("from", DATE)
						.param("to", DATE)
						.param("sectionId", CLASS_SECTION_B))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CONTENT_TYPE,
						"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"attendance-student-" + DATE + "-to-" + DATE + ".xlsx\""))
				.andReturn();

		List<List<String>> rows = readSheet(result.getResponse().getContentAsByteArray());
		assertThat(rows.getFirst()).containsExactly(
				"Date", "Class-Section", "Roll No", "Student Name", "Status", "Method",
				"Marked By", "Marked At", "Last Updated At", "Remarks");
		List<String> row = rows.stream().filter(r -> r.contains(studentName)).findFirst().orElseThrow();
		assertThat(row.get(0)).isEqualTo(DATE);
		assertThat(row.get(4)).isEqualTo("LATE");
		assertThat(row.get(6)).isEqualTo("Export Teacher");
		assertThat(row.get(7)).matches(TIMESTAMP_PATTERN);
		assertThat(row.get(8)).matches(TIMESTAMP_PATTERN);
		assertThat(row.get(9)).isEqualTo("Bus delay");
	}

	@Test
	void staffExportContainsMarkedRecord() throws Exception {
		String employeeName = "Export Staff " + UUID.randomUUID().toString().substring(0, 6);
		String employeeId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, employeeName);
		String markerId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Export HR");

		mockMvc.perform(post("/api/v1/staff-attendance")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"date": "%s", "markedByEmployeeId": "%s",
								 "records": [{"employeeId": "%s", "status": "PRESENT"}]}
								""".formatted(DATE, markerId, employeeId)))
				.andExpect(status().isOk());

		MvcResult result = mockMvc.perform(get("/api/v1/attendance/export")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, adminBearer)
						.param("type", "STAFF")
						.param("from", DATE)
						.param("to", DATE))
				.andExpect(status().isOk())
				.andReturn();

		List<List<String>> rows = readSheet(result.getResponse().getContentAsByteArray());
		assertThat(rows.getFirst().get(1)).isEqualTo("Name");
		List<String> row = rows.stream().filter(r -> r.contains(employeeName)).findFirst().orElseThrow();
		assertThat(row.get(3)).isEqualTo("PRESENT");
		assertThat(row.get(5)).isEqualTo("No");
		assertThat(row.get(6)).isEqualTo("Export HR");
		assertThat(row.get(7)).matches(TIMESTAMP_PATTERN);
	}

	@Test
	void nonAdminIsForbidden() throws Exception {
		String employeeId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Export Teacher Reader");
		String teacherToken = AuthTestSupport.provisionAndLogin(
				mockMvc, SCHOOL_ID, adminBearer.substring("Bearer ".length()), "employees", employeeId, "TEACHER");

		mockMvc.perform(get("/api/v1/attendance/export")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherToken)
						.param("type", "STUDENT")
						.param("from", DATE)
						.param("to", DATE))
				.andExpect(status().isForbidden());
	}

	@Test
	void invalidDateRangesAreRejected() throws Exception {
		mockMvc.perform(get("/api/v1/attendance/export")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, adminBearer)
						.param("type", "STUDENT")
						.param("from", "2026-03-10")
						.param("to", "2026-03-01"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/v1/attendance/export")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, adminBearer)
						.param("type", "STUDENT")
						.param("from", "2025-01-01")
						.param("to", "2026-03-01"))
				.andExpect(status().isBadRequest());
	}

	private static List<List<String>> readSheet(byte[] xlsx) throws Exception {
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
			Sheet sheet = workbook.getSheetAt(0);
			List<List<String>> rows = new ArrayList<>();
			for (Row row : sheet) {
				List<String> cells = new ArrayList<>();
				for (int i = 0; i < row.getLastCellNum(); i++) {
					cells.add(row.getCell(i) != null ? row.getCell(i).getStringCellValue() : "");
				}
				rows.add(cells);
			}
			return rows;
		}
	}

}
