package com.gurukul.calls.googlemeet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.crypto.TokenCipher;
import com.gurukul.employees.entity.Employee;
import com.gurukul.employees.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Server-side OAuth authorization-code flow to get a per-teacher refresh token for the Calendar
 * API's calendar.events scope - distinct from GoogleTokenVerifier, which only verifies an
 * already-issued Sign-In ID token and never touches Calendar access. Plain HttpClient + Jackson
 * rather than pulling in google-api-services-calendar - it's two simple REST calls (token
 * exchange, userinfo), not worth a whole extra client dependency.
 */
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(GoogleMeetProperties.class)
public class GoogleOAuthService {

	private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
	private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
	private static final String USERINFO_ENDPOINT = "https://openidconnect.googleapis.com/v1/userinfo";
	private static final String SCOPE = "https://www.googleapis.com/auth/calendar.events openid email";

	private final GoogleMeetProperties properties;
	private final TeacherGoogleCredentialRepository credentialRepository;
	private final EmployeeRepository employeeRepository;
	private final TokenCipher tokenCipher;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final HttpClient httpClient = HttpClient.newHttpClient();

	@Value("${app.jwt.secret}")
	private String stateSigningSecret;

	/**
	 * state is employeeId + a signature (HMAC-SHA256 over the employeeId, keyed with the app's own
	 * JWT secret) so the callback - which Google calls directly on the teacher's browser, with no
	 * Authorization header of ours to check - can trust which employee this authorization is for
	 * without a forgeable plain UUID.
	 */
	public String buildAuthorizationUrl(UUID employeeId) {
		if (!properties.isConfigured()) {
			throw new IllegalStateException("Google Meet is not configured on this server");
		}
		String state = employeeId + "." + sign(employeeId.toString());
		String params = "client_id=" + encode(properties.clientId())
				+ "&redirect_uri=" + encode(properties.redirectUri())
				+ "&response_type=code"
				+ "&scope=" + encode(SCOPE)
				+ "&access_type=offline"
				+ "&prompt=consent"
				+ "&state=" + encode(state);
		return AUTH_ENDPOINT + "?" + params;
	}

	@Transactional
	public String handleCallback(String code, String state) {
		UUID employeeId = verifyAndDecodeState(state);
		Employee employee = employeeRepository.findById(employeeId)
				.orElseThrow(() -> new EntityNotFoundException("No employee found for this Google connection"));
		TokenExchangeResult tokens = exchangeCodeForTokens(code);
		String googleEmail = fetchEmail(tokens.accessToken());
		TeacherGoogleCredential credential = credentialRepository.findByEmployeeId(employeeId)
				.orElseGet(TeacherGoogleCredential::new);
		credential.setSchoolId(employee.getSchoolId());
		credential.setEmployeeId(employeeId);
		credential.setGoogleEmail(googleEmail);
		credential.setEncryptedRefreshToken(tokenCipher.encrypt(tokens.refreshToken()));
		credentialRepository.save(credential);
		return googleEmail;
	}

	public boolean isConnected(UUID employeeId) {
		return credentialRepository.findByEmployeeId(employeeId).isPresent();
	}

	public java.util.Optional<String> connectedEmail(UUID employeeId) {
		return credentialRepository.findByEmployeeId(employeeId).map(TeacherGoogleCredential::getGoogleEmail);
	}

	@Transactional
	public void disconnect(UUID employeeId) {
		credentialRepository.deleteByEmployeeId(employeeId);
	}

	/** Package-private: GoogleMeetService needs a fresh access token per meeting-creation call. */
	String mintAccessToken(UUID employeeId) {
		TeacherGoogleCredential credential = credentialRepository.findByEmployeeId(employeeId)
				.orElseThrow(() -> new IllegalStateException("This teacher hasn't connected Google Calendar"));
		String refreshToken = tokenCipher.decrypt(credential.getEncryptedRefreshToken());
		return refreshAccessToken(refreshToken);
	}

	private record TokenExchangeResult(String refreshToken, String accessToken) {
	}

	private TokenExchangeResult exchangeCodeForTokens(String code) {
		String body = "code=" + encode(code)
				+ "&client_id=" + encode(properties.clientId())
				+ "&client_secret=" + encode(properties.clientSecret())
				+ "&redirect_uri=" + encode(properties.redirectUri())
				+ "&grant_type=authorization_code";
		JsonNode response = postForm(TOKEN_ENDPOINT, body);
		JsonNode refreshTokenNode = response.get("refresh_token");
		if (refreshTokenNode == null) {
			throw new IllegalStateException(
					"Google did not return a refresh token - this teacher may already have an active "
							+ "grant; revoke access at myaccount.google.com/permissions and try connecting again");
		}
		return new TokenExchangeResult(refreshTokenNode.asText(), response.get("access_token").asText());
	}

	private String refreshAccessToken(String refreshToken) {
		String body = "refresh_token=" + encode(refreshToken)
				+ "&client_id=" + encode(properties.clientId())
				+ "&client_secret=" + encode(properties.clientSecret())
				+ "&grant_type=refresh_token";
		return postForm(TOKEN_ENDPOINT, body).get("access_token").asText();
	}

	private String fetchEmail(String accessToken) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(USERINFO_ENDPOINT))
					.header("Authorization", "Bearer " + accessToken)
					.GET()
					.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				throw new IllegalStateException("Failed to fetch Google account email: HTTP " + response.statusCode());
			}
			return objectMapper.readTree(response.body()).get("email").asText();
		} catch (java.io.IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw new IllegalStateException("Failed to fetch Google account email", e);
		}
	}

	private JsonNode postForm(String url, String body) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString(body))
					.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			JsonNode json = objectMapper.readTree(response.body());
			if (response.statusCode() != 200) {
				throw new IllegalStateException("Google OAuth request failed: " + json);
			}
			return json;
		} catch (java.io.IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw new IllegalStateException("Google OAuth request failed", e);
		}
	}

	private UUID verifyAndDecodeState(String state) {
		int lastDot = state.lastIndexOf('.');
		if (lastDot < 0) {
			throw new IllegalArgumentException("Invalid state parameter");
		}
		String employeeIdPart = state.substring(0, lastDot);
		String signaturePart = state.substring(lastDot + 1);
		if (!sign(employeeIdPart).equals(signaturePart)) {
			throw new IllegalArgumentException("Invalid state parameter - signature mismatch");
		}
		return UUID.fromString(employeeIdPart);
	}

	private String sign(String value) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(stateSigningSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		} catch (java.security.GeneralSecurityException e) {
			throw new IllegalStateException("Failed to sign state parameter", e);
		}
	}

	private String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

}
