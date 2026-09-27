package com.gurukul.fees;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The webhook has to survive two filters that every other /api/v1/ request is required to satisfy:
 * SecurityConfig (no JWT) and SchoolContextFilter (no X-School-Id header). Razorpay can supply
 * neither. Both exemptions are easy to break from a distance - tightening the blanket permitAll(),
 * or adding a school-scoping rule - and the failure is silent in the sense that payments simply stop
 * being credited, with nothing surfacing in the app.
 *
 * <p>These tests assert the request reaches the controller, not that it succeeds: with no webhook
 * secret configured in tests, verification correctly fails closed with 403. The point is that 403 is
 * a signature verdict, and specifically NOT the 400 "Missing X-School-Id header" the filter would
 * return if the exemption were lost.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RazorpayWebhookIntegrationTest {

	private static final String CAPTURED_EVENT = """
			{"event":"payment.captured","payload":{"payment":{"entity":
			{"id":"pay_TEST","order_id":"order_TEST","status":"captured","method":"upi","amount":1000}}}}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void webhookIsReachableWithoutAJwtOrASchoolHeader() throws Exception {
		mockMvc.perform(post("/api/v1/webhooks/razorpay")
						.header("X-Razorpay-Signature", "not-a-valid-signature")
						.contentType(MediaType.APPLICATION_JSON)
						.content(CAPTURED_EVENT))
				.andExpect(status().isForbidden())
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("X-School-Id"))));
	}

	@Test
	void webhookWithNoSignatureHeaderIsRejectedRatherThanTrusted() throws Exception {
		// Fail closed. An unsigned body reaching the handler would let anyone mark any fee paid by
		// POSTing a payment.captured event for an order id they guessed.
		mockMvc.perform(post("/api/v1/webhooks/razorpay")
						.contentType(MediaType.APPLICATION_JSON)
						.content(CAPTURED_EVENT))
				.andExpect(status().isForbidden());
	}

}
