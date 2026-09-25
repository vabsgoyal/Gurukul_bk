package com.gurukul.auth.whatsapp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WhatsAppSessionResolverTest {

	private static final String SESSIONS_URL = "http://wa-akg/api/sessions";

	private MockRestServiceServer server;
	private RestClient restClient;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://wa-akg");
		server = MockRestServiceServer.bindTo(builder).build();
		restClient = builder.build();
	}

	private WhatsAppSessionResolver resolver(String fallbackId, String name) {
		return new WhatsAppSessionResolver(restClient,
				new WhatsAppOtpProperties("http://wa-akg", "token", fallbackId, name, 30, 4, 5));
	}

	private void respondWithSessions(String json) {
		server.expect(once(), requestTo(SESSIONS_URL))
				.andRespond(withSuccess("{\"status\":true,\"data\":" + json + "}", MediaType.APPLICATION_JSON));
	}

	@Test
	void resolvesSessionIdByName() {
		respondWithSessions("""
				[{"sessionId":"other1","name":"Marketing","status":"CONNECTED","botConfig":{}},
				 {"sessionId":"abc123","name":"Smart Gurukul","status":"CONNECTED","_count":{"messages":5}}]""");

		assertThat(resolver("", "Smart Gurukul").resolveSessionId()).isEqualTo("abc123");
	}

	@Test
	void prefersConnectedSessionWhenNameIsShared() {
		respondWithSessions("""
				[{"sessionId":"newdead","name":"Smart Gurukul","status":"STOPPED"},
				 {"sessionId":"live42","name":"smart gurukul ","status":"CONNECTED"}]""");

		assertThat(resolver("", "Smart Gurukul").resolveSessionId()).isEqualTo("live42");
	}

	@Test
	void cachesTheResolvedIdUntilInvalidated() {
		WhatsAppSessionResolver resolver = resolver("", "Smart Gurukul");
		respondWithSessions("[{\"sessionId\":\"first\",\"name\":\"Smart Gurukul\",\"status\":\"CONNECTED\"}]");

		assertThat(resolver.resolveSessionId()).isEqualTo("first");
		assertThat(resolver.resolveSessionId()).isEqualTo("first"); // no second request expected
		server.verify();

		server.reset();
		respondWithSessions("[{\"sessionId\":\"second\",\"name\":\"Smart Gurukul\",\"status\":\"CONNECTED\"}]");
		resolver.invalidate();

		assertThat(resolver.resolveSessionId()).isEqualTo("second");
		server.verify();
	}

	@Test
	void fallsBackToConfiguredIdWhenNoSessionHasTheName() {
		respondWithSessions("[{\"sessionId\":\"x\",\"name\":\"Something else\",\"status\":\"CONNECTED\"}]");

		assertThat(resolver("ifekcn", "Smart Gurukul").resolveSessionId()).isEqualTo("ifekcn");
	}

	@Test
	void fallsBackToConfiguredIdWhenGatewayLookupFails() {
		server.expect(once(), requestTo(SESSIONS_URL)).andRespond(withServerError());

		assertThat(resolver("ifekcn", "Smart Gurukul").resolveSessionId()).isEqualTo("ifekcn");
	}

	@Test
	void failsSoftWhenNothingResolvesAndNoFallbackIsConfigured() {
		respondWithSessions("[]");

		assertThatThrownBy(() -> resolver("", "Smart Gurukul").resolveSessionId())
				.isInstanceOf(WhatsAppOtpDeliveryException.class);
	}

}
