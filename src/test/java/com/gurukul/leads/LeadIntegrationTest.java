package com.gurukul.leads;

import com.gurukul.leads.entity.LeadType;
import com.gurukul.leads.repository.DemoLeadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The marketing-site demo form: public POST with honeypot + per-address rate limit, CORS scoped to
 * this one path, and a token-gated GET listing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.leads.admin-token=test-leads-token")
class LeadIntegrationTest {

	private static final String SITE = "https://smartgurukul.org";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DemoLeadRepository demoLeadRepository;

	@BeforeEach
	void clean() {
		demoLeadRepository.deleteAll();
	}

	/** A unique forwarded address per test so the per-IP limit never leaks between tests. */
	private static String freshIp() {
		UUID u = UUID.randomUUID();
		return "10." + (u.hashCode() & 0x7f) + "." + ((u.hashCode() >> 8) & 0xff) + "." + ((u.hashCode() >> 16) & 0xff);
	}

	private static MockHttpServletRequestBuilder submit(String body, String ip) {
		return post("/api/v1/leads")
				.header("X-Forwarded-For", ip)
				.header(HttpHeaders.ORIGIN, SITE)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body);
	}

	private static String validLead(String school) {
		return """
				{"name": "Asha Verma", "schoolName": "%s", "role": "Principal", "phone": "+91 98765 43210",
				 "email": "asha@example.org", "city": "Indore", "state": "Madhya Pradesh",
				 "studentCount": "200-500", "message": "Keen on GPS attendance", "sourcePage": "/"}
				""".formatted(school);
	}

	@Test
	void validLeadIsStoredWithoutRawIpAndReturns202() throws Exception {
		String ip = freshIp();
		mockMvc.perform(submit(validLead("Sunrise Public School"), ip))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.status").value("received"))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, SITE));

		assertThat(demoLeadRepository.findAll()).singleElement().satisfies(lead -> {
			assertThat(lead.getSchoolName()).isEqualTo("Sunrise Public School");
			assertThat(lead.getPhone()).isEqualTo("+91 98765 43210");
			assertThat(lead.getIpHash()).hasSize(64).doesNotContain(ip);
		});
	}

	@Test
	void websiteServicesRequestIsStoredWithItsServices() throws Exception {
		mockMvc.perform(submit("""
						{"name": "Ravi Jain", "schoolName": "Green Valley School", "phone": "9876543210",
						 "email": "ravi@example.org", "requestType": "WEBSITE_SERVICES",
						 "services": ["New school website", " Online admission form ", "New school website", ""],
						 "budget": "15k-30k", "sourcePage": "/website-services.html"}
						""", freshIp()))
				.andExpect(status().isAccepted());

		assertThat(demoLeadRepository.findAll()).singleElement().satisfies(lead -> {
			assertThat(lead.getRequestType()).isEqualTo(LeadType.WEBSITE_SERVICES);
			assertThat(lead.getServices()).isEqualTo("New school website, Online admission form");
			assertThat(lead.getBudget()).isEqualTo("15k-30k");
		});
	}

	@Test
	void requestTypeDefaultsToDemoAndUnknownTypesAreRejected() throws Exception {
		mockMvc.perform(submit(validLead("Default Type School"), freshIp())).andExpect(status().isAccepted());
		assertThat(demoLeadRepository.findAll()).singleElement()
				.satisfies(lead -> assertThat(lead.getRequestType()).isEqualTo(LeadType.DEMO));

		mockMvc.perform(submit("""
						{"name": "A", "schoolName": "S", "phone": "9876543210", "requestType": "HACK"}
						""", freshIp()))
				.andExpect(status().isBadRequest());
		mockMvc.perform(submit("""
						{"name": "A", "schoolName": "S", "phone": "9876543210", "services": ["%s"]}
						""".formatted("x".repeat(41)), freshIp()))
				.andExpect(status().isBadRequest());
		assertThat(demoLeadRepository.count()).isEqualTo(1);
	}

	@Test
	void needsNoSchoolHeaderOrAuth() throws Exception {
		mockMvc.perform(post("/api/v1/leads")
						.contentType(MediaType.APPLICATION_JSON)
						.content(validLead("No Header School")))
				.andExpect(status().isAccepted());
	}

	@Test
	void invalidFieldsAreRejected() throws Exception {
		mockMvc.perform(submit("""
						{"name": "", "schoolName": "X", "phone": "12345", "email": "not-an-email"}
						""", freshIp()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message", containsString("phone")))
				.andExpect(jsonPath("$.message", containsString("name")))
				.andExpect(jsonPath("$.message", containsString("email")));

		mockMvc.perform(submit("""
						{"name": "A", "schoolName": "%s", "phone": "9876543210"}
						""".formatted("x".repeat(151)), freshIp()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", containsString("schoolName")));

		assertThat(demoLeadRepository.count()).isZero();
	}

	@Test
	void acceptsCommonIndianMobileFormats() throws Exception {
		for (String phone : new String[] {"9876543210", "+919876543210", "09876543210", "98765-43210"}) {
			mockMvc.perform(submit("""
							{"name": "A", "schoolName": "S", "phone": "%s"}
							""".formatted(phone), freshIp()))
					.andExpect(status().isAccepted());
		}
		// Landlines / numbers starting 0-5 aren't mobiles.
		mockMvc.perform(submit("""
						{"name": "A", "schoolName": "S", "phone": "5876543210"}
						""", freshIp()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void honeypotSubmissionLooksAcceptedButIsDropped() throws Exception {
		mockMvc.perform(submit("""
						{"name": "Bot", "schoolName": "Spam", "phone": "9876543210", "website": "http://spam.example"}
						""", freshIp()))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.data.status").value("received"));

		assertThat(demoLeadRepository.count()).isZero();
	}

	@Test
	void sixthSubmissionFromSameAddressWithinAnHourIsRateLimited() throws Exception {
		String ip = freshIp();
		for (int i = 0; i < 5; i++) {
			mockMvc.perform(submit(validLead("School " + i), ip)).andExpect(status().isAccepted());
		}
		mockMvc.perform(submit(validLead("School 6"), ip))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.errorCode").value("RATE_LIMITED"));

		// A different address is unaffected.
		mockMvc.perform(submit(validLead("Other"), freshIp())).andExpect(status().isAccepted());
		assertThat(demoLeadRepository.count()).isEqualTo(6);
	}

	@Test
	void spoofedLeadingForwardedEntriesDontDodgeTheLimit() throws Exception {
		String proxySeen = freshIp();
		for (int i = 0; i < 5; i++) {
			mockMvc.perform(submit(validLead("S" + i), "1.2.3." + i + ", " + proxySeen)).andExpect(status().isAccepted());
		}
		mockMvc.perform(submit(validLead("S6"), "9.9.9.9, " + proxySeen))
				.andExpect(status().isTooManyRequests());
	}

	@Test
	void preflightFromMarketingSiteIsAllowed() throws Exception {
		mockMvc.perform(options("/api/v1/leads")
						.header(HttpHeaders.ORIGIN, SITE)
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, SITE))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));

		mockMvc.perform(options("/api/v1/leads")
						.header(HttpHeaders.ORIGIN, "https://www.smartgurukul.org")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://www.smartgurukul.org"));
	}

	@Test
	void preflightFromOtherOriginIsRejected() throws Exception {
		mockMvc.perform(options("/api/v1/leads")
						.header(HttpHeaders.ORIGIN, "https://evil.example")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	void preflightForGetOnLeadsIsRejected() throws Exception {
		mockMvc.perform(options("/api/v1/leads")
						.header(HttpHeaders.ORIGIN, SITE)
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isForbidden());
	}

	@Test
	void otherPathsGetNoCorsHeaders() throws Exception {
		mockMvc.perform(get("/api/v1/schools").header(HttpHeaders.ORIGIN, SITE))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

		mockMvc.perform(options("/api/v1/schools")
						.header(HttpHeaders.ORIGIN, SITE)
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	void adminListingRequiresTheToken() throws Exception {
		mockMvc.perform(submit(validLead("Listed School"), freshIp())).andExpect(status().isAccepted());

		mockMvc.perform(get("/api/v1/leads")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/leads").header(HttpHeaders.AUTHORIZATION, "Bearer wrong"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/leads").header(HttpHeaders.AUTHORIZATION, "Bearer test-leads-token"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].schoolName").value("Listed School"))
				.andExpect(jsonPath("$.data[0].ipHash").doesNotExist());
	}

}
