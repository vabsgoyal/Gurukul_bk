package com.gurukul.calls.googlemeet;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.security.AuthContext;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Calls - Google Meet", description = "Per-teacher Google account connection, used to create real "
		+ "Google Meet links (free, no Workspace required) as an alternative to the Jitsi bot. Only the "
		+ "connecting teacher's own account can admit guests who join a Meet without a Google account - see "
		+ "docs/google-meet-setup.md for why this can't be a single shared account.")
public class GoogleMeetConnectionController {

	private final GoogleOAuthService googleOAuthService;

	@PostMapping("/api/v1/calls/google/connect")
	@Operation(summary = "Get the Google consent URL to connect this teacher's own Google account")
	public ApiResponse<String> connect() {
		AuthPrincipal principal = requireEmployee();
		return ApiResponse.success(googleOAuthService.buildAuthorizationUrl(principal.getOwnerId()));
	}

	/**
	 * Public endpoint (see SchoolContextFilter) - Google redirects the teacher's browser here
	 * directly, with no Authorization/X-School-Id header of ours to check. The employee this
	 * authorization is for comes from the signed state param, not from a session.
	 */
	@GetMapping("/api/v1/calls/google/callback")
	@Operation(summary = "OAuth redirect target - not called directly by the app")
	public ApiResponse<String> callback(@RequestParam String code, @RequestParam String state) {
		String googleEmail = googleOAuthService.handleCallback(code, state);
		return ApiResponse.success(googleEmail, "Google account connected - you can close this window");
	}

	@GetMapping("/api/v1/calls/google/status")
	@Operation(summary = "Whether this teacher has connected a Google account, and which email")
	public ApiResponse<GoogleConnectionStatus> status() {
		AuthPrincipal principal = requireEmployee();
		return ApiResponse.success(new GoogleConnectionStatus(
				googleOAuthService.isConnected(principal.getOwnerId()),
				googleOAuthService.connectedEmail(principal.getOwnerId()).orElse(null)));
	}

	@DeleteMapping("/api/v1/calls/google/disconnect")
	@Operation(summary = "Disconnect this teacher's Google account")
	public ApiResponse<Void> disconnect() {
		AuthPrincipal principal = requireEmployee();
		googleOAuthService.disconnect(principal.getOwnerId());
		return ApiResponse.success(null, "Google account disconnected");
	}

	private AuthPrincipal requireEmployee() {
		AuthPrincipal principal = AuthContext.current();
		if (principal.getOwnerType() != OwnerType.EMPLOYEE) {
			throw new AccessDeniedException("Only a teacher or admin can connect a Google account for calls");
		}
		return principal;
	}

	public record GoogleConnectionStatus(boolean connected, String googleEmail) {
	}

}
