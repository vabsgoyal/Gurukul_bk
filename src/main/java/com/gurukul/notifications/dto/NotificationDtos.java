package com.gurukul.notifications.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

public class NotificationDtos {

	@Getter @Setter
	public static class RegisterDeviceTokenRequest {
		@NotBlank private String expoPushToken;
	}

}
