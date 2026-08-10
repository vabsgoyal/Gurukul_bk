package com.gurukul.registration;

import com.gurukul.auth.AuthTestSupport;
import com.gurukul.registration.repository.TeacherInviteRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Re-inviting the same employee should be idempotent (return the still-valid existing code)
 * rather than erroring - see TeacherInviteService.createInviteForEmployee.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TeacherInviteIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TeacherInviteRepository teacherInviteRepository;

	@Test
	void invitingSameEmployeeTwiceReturnsTheSameUnexpiredCode() throws Exception {
		String employeeId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Reinvite Test Teacher");
		String adminBearer = "Bearer " + AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);

		MvcResult first = mockMvc.perform(post("/api/v1/employees/" + employeeId + "/invite")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, adminBearer))
				.andExpect(status().isOk())
				.andReturn();
		String firstCode = JsonPath.read(first.getResponse().getContentAsString(), "$.data.code");

		MvcResult second = mockMvc.perform(post("/api/v1/employees/" + employeeId + "/invite")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, adminBearer))
				.andExpect(status().isOk())
				.andReturn();
		String secondCode = JsonPath.read(second.getResponse().getContentAsString(), "$.data.code");

		assertThat(secondCode).isEqualTo(firstCode);
	}

	@Test
	@Transactional
	void invitingAgainAfterExpiryIssuesAFreshCode() throws Exception {
		String employeeId = AuthTestSupport.createEmployee(mockMvc, SCHOOL_ID, "Expired Invite Teacher");
		String adminBearer = "Bearer " + AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);

		MvcResult first = mockMvc.perform(post("/api/v1/employees/" + employeeId + "/invite")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, adminBearer))
				.andExpect(status().isOk())
				.andReturn();
		String firstCode = JsonPath.read(first.getResponse().getContentAsString(), "$.data.code");

		var invite = teacherInviteRepository.findBySchoolIdAndTargetEmployeeIdAndUsedFalse(UUID.fromString(SCHOOL_ID), UUID.fromString(employeeId))
				.orElseThrow();
		invite.setExpiresAt(Instant.now().minusSeconds(3600));
		teacherInviteRepository.save(invite);

		MvcResult second = mockMvc.perform(post("/api/v1/employees/" + employeeId + "/invite")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, adminBearer))
				.andExpect(status().isOk())
				.andReturn();
		String secondCode = JsonPath.read(second.getResponse().getContentAsString(), "$.data.code");

		assertThat(secondCode).isNotEqualTo(firstCode);
	}

}
