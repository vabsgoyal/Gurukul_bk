package com.gurukul.calls.googlemeet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Creates a real Google Meet link via the Calendar API's events.insert with conferenceData
 * (conferenceDataVersion=1, hangoutsMeet) - the free, no-Workspace-required path (see
 * docs/google-meet-setup.md). Requires the given teacher to have already connected their Google
 * account via GoogleOAuthService; only that teacher (as the meeting's creator) will be able to
 * admit guests who join without a Google account, which is why this is per-teacher rather than one
 * shared account.
 */
@Service
@RequiredArgsConstructor
public class GoogleMeetService {

	private static final String EVENTS_ENDPOINT = "https://www.googleapis.com/calendar/v3/calendars/primary/events?conferenceDataVersion=1";

	private final GoogleOAuthService googleOAuthService;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final HttpClient httpClient = HttpClient.newHttpClient();

	public boolean isConnected(UUID teacherEmployeeId) {
		return googleOAuthService.isConnected(teacherEmployeeId);
	}

	/** Returns the meeting's join URL (a full https://meet.google.com/... link). */
	public String createMeeting(UUID teacherEmployeeId, String summary, Instant startTime, Instant endTime) {
		String accessToken = googleOAuthService.mintAccessToken(teacherEmployeeId);
		String requestBody = buildEventJson(summary, startTime, endTime);
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(EVENTS_ENDPOINT))
					.header("Authorization", "Bearer " + accessToken)
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
					.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			JsonNode json = objectMapper.readTree(response.body());
			if (response.statusCode() != 200) {
				throw new IllegalStateException("Failed to create Google Meet event: " + json);
			}
			return extractMeetLink(json);
		} catch (java.io.IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw new IllegalStateException("Failed to create Google Meet event", e);
		}
	}

	private String buildEventJson(String summary, Instant startTime, Instant endTime) {
		return """
				{
				  "summary": "%s",
				  "start": {"dateTime": "%s"},
				  "end": {"dateTime": "%s"},
				  "conferenceData": {
				    "createRequest": {
				      "requestId": "%s",
				      "conferenceSolutionKey": {"type": "hangoutsMeet"}
				    }
				  }
				}
				""".formatted(
				summary.replace("\"", "\\\""),
				startTime.toString(),
				endTime.toString(),
				UUID.randomUUID());
	}

	private String extractMeetLink(JsonNode event) {
		JsonNode entryPoints = event.path("conferenceData").path("entryPoints");
		for (JsonNode entryPoint : entryPoints) {
			if ("video".equals(entryPoint.path("entryPointType").asText())) {
				return entryPoint.path("uri").asText();
			}
		}
		throw new IllegalStateException("Google Calendar did not return a Meet video link: " + event);
	}

}
