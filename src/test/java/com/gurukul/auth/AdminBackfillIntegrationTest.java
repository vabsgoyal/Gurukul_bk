package com.gurukul.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminBackfillIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void wrongOpsKeyIsRejected() throws Exception {
		mockMvc.perform(post("/api/v1/ops/admin-backfill")
						.header("X-Ops-Key", "not-the-real-key"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void missingOpsKeyIsRejected() throws Exception {
		mockMvc.perform(post("/api/v1/ops/admin-backfill"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void backfillIsIdempotentAndNeverTouchesSchoolsThatAlreadyHaveAnAdmin() throws Exception {
		MvcResult first = mockMvc.perform(post("/api/v1/ops/admin-backfill")
						.header("X-Ops-Key", "dev-only-ops-key-change-me-before-prod"))
				.andExpect(status().isOk())
				.andReturn();
		int firstCreatedCount = JsonPath.read(first.getResponse().getContentAsString(), "$.data.schoolsProcessed");

		mockMvc.perform(post("/api/v1/ops/admin-backfill")
						.header("X-Ops-Key", "dev-only-ops-key-change-me-before-prod"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.schoolsProcessed").value(0))
				.andExpect(jsonPath("$.data.created.length()").value(0));

		org.assertj.core.api.Assertions.assertThat(firstCreatedCount).isGreaterThanOrEqualTo(0);
	}

}
