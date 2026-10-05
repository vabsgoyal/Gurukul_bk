package com.gurukul.notifications.controller;

import com.gurukul.auth.security.AuthContext;
import com.gurukul.common.ApiResponse;
import com.gurukul.notifications.dto.NotificationDtos.RegisterDeviceTokenRequest;
import com.gurukul.notifications.service.PushNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Push notification device registration. Requires X-School-Id header.")
public class DeviceTokenController {

	private final PushNotificationService pushNotificationService;

	@PostMapping("/api/v1/notifications/device-token")
	@Operation(summary = "Register (or re-register) this device's Expo push token for the current session",
			description = "Call after login and whenever expo-notifications reports a new token. Safe to call repeatedly with the same token.")
	public ApiResponse<Void> registerDeviceToken(@Valid @RequestBody RegisterDeviceTokenRequest request) {
		pushNotificationService.registerToken(AuthContext.current(), request.getExpoPushToken());
		return ApiResponse.success(null, "Device registered");
	}

	@DeleteMapping("/api/v1/notifications/device-token")
	@Operation(summary = "Unregister this device's Expo push token (call on logout)",
			description = "Pass the token as the expoPushToken query parameter or as a JSON body {\"expoPushToken\": \"...\"}. "
					+ "Removes it only if it is registered to the caller; always answers 204, so it is safe to repeat.")
	public ResponseEntity<Void> unregisterDeviceToken(
			@RequestParam(name = "expoPushToken", required = false) String expoPushTokenParam,
			@RequestBody(required = false) RegisterDeviceTokenRequest request) {
		String token = expoPushTokenParam != null && !expoPushTokenParam.isBlank()
				? expoPushTokenParam
				: request != null ? request.getExpoPushToken() : null;
		if (token == null || token.isBlank()) {
			throw new IllegalArgumentException("expoPushToken is required");
		}
		pushNotificationService.unregisterToken(AuthContext.current(), token);
		return ResponseEntity.noContent().build();
	}

}
