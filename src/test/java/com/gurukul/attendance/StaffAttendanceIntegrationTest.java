package com.gurukul.attendance;

import com.gurukul.auth.AuthTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StaffAttendanceIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void markStaffAttendanceThenReadRosterAndEmployeeHistory() throws Exception {
		MvcResult staffResult = mockMvc.perform(post("/api/v1/employees")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Accounts Staff", "designation": "Accountant", "joinDate": "2024-04-01", "employeeType": "NON_TEACHING"}
								"""))
				.andExpect(status().isOk())
				.andReturn();
		String employeeId = JsonPath.read(staffResult.getResponse().getContentAsString(), "$.data.id");

		MvcResult adminResult = mockMvc.perform(post("/api/v1/employees")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "HR Admin", "designation": "Admin", "joinDate": "2024-04-01"}
								"""))
				.andExpect(status().isOk())
				.andReturn();
		String adminId = JsonPath.read(adminResult.getResponse().getContentAsString(), "$.data.id");

		String bearer = "Bearer " + AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);

		String markPayload = """
				{
				  "date": "2026-08-05",
				  "markedByEmployeeId": "%s",
				  "records": [
				    {"employeeId": "%s", "status": "PRESENT"}
				  ]
				}
				""".formatted(adminId, employeeId);

		mockMvc.perform(post("/api/v1/staff-attendance")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(markPayload))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.entries[?(@.employeeId == '" + employeeId + "')].status").value("PRESENT"));

		mockMvc.perform(get("/api/v1/staff-attendance")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, bearer)
						.param("date", "2026-08-05"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.entries[?(@.employeeId == '" + employeeId + "')].status").value("PRESENT"));

		String remarkPayload = """
				{
				  "date": "2026-08-05",
				  "markedByEmployeeId": "%s",
				  "records": [
				    {"employeeId": "%s", "status": "HALF_DAY", "remarks": "Left early"}
				  ]
				}
				""".formatted(adminId, employeeId);

		mockMvc.perform(post("/api/v1/staff-attendance")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(remarkPayload))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.entries[?(@.employeeId == '" + employeeId + "')].status").value("HALF_DAY"));

		mockMvc.perform(get("/api/v1/employees/" + employeeId + "/attendance")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalRecords").value(1))
				.andExpect(jsonPath("$.data.halfDayCount").value(1))
				.andExpect(jsonPath("$.data.presentCount").value(0));
	}

}
