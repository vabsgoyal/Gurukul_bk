package com.gurukul.attendance.dto;

import com.gurukul.attendance.entity.AttendanceDevice;
import com.gurukul.attendance.entity.AttendanceIdentifier;
import com.gurukul.attendance.entity.AttendanceMethod;
import com.gurukul.attendance.entity.AttendanceStatus;
import com.gurukul.auth.entity.OwnerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class AttendanceDeviceDtos {

	@Getter @Setter
	public static class CreateDeviceRequest {
		@NotBlank private String name;
		@NotNull private AttendanceMethod deviceType;
	}

	@Getter @Setter
	public static class UpdateDeviceRequest {
		@NotBlank private String name;
		@NotNull private Boolean active;
	}

	@Getter @AllArgsConstructor
	public static class DeviceResponse {
		private UUID id;
		private String name;
		private AttendanceMethod deviceType;
		private boolean active;
		private Instant lastSeenAt;
		private Instant createdAt;

		public static DeviceResponse from(AttendanceDevice device) {
			return new DeviceResponse(
					device.getId(), device.getName(), device.getDeviceType(), device.isActive(),
					device.getLastSeenAt(), device.getCreatedAt());
		}
	}

	@Getter @AllArgsConstructor
	@Schema(description = "Returned only at device creation/key-rotation time - the plaintext apiKey is never shown again")
	public static class DeviceKeyResponse {
		private UUID id;
		private String name;
		private AttendanceMethod deviceType;
		private String apiKey;
	}

	@Getter @Setter
	public static class EnrollIdentifierRequest {
		@NotNull private AttendanceMethod method;
		@NotBlank private String externalId;
	}

	@Getter @AllArgsConstructor
	public static class IdentifierResponse {
		private UUID id;
		private OwnerType ownerType;
		private UUID ownerId;
		private String ownerName;
		private AttendanceMethod method;
		private String externalId;
		private boolean active;
		private Instant createdAt;

		public static IdentifierResponse from(AttendanceIdentifier identifier, String ownerName) {
			return new IdentifierResponse(
					identifier.getId(), identifier.getOwnerType(), identifier.getOwnerId(), ownerName,
					identifier.getMethod(), identifier.getExternalId(), identifier.isActive(), identifier.getCreatedAt());
		}
	}

	@Getter @Setter
	@Schema(description = "Sent by a registered device after it resolves a scan to an external id; the device's "
			+ "own registered type determines the method, so it isn't re-sent here")
	public static class DeviceEventRequest {
		@NotBlank private String externalId;
		@Schema(description = "Defaults to the server's current time when omitted")
		private Instant capturedAt;
	}

	@Getter @AllArgsConstructor
	public static class DeviceEventResponse {
		private OwnerType ownerType;
		private UUID ownerId;
		private String ownerName;
		private AttendanceStatus status;
		private LocalDate attendanceDate;
	}

}
