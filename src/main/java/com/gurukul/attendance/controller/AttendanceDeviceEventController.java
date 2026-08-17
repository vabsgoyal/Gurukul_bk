package com.gurukul.attendance.controller;

import com.gurukul.attendance.dto.AttendanceDeviceDtos.DeviceEventRequest;
import com.gurukul.attendance.dto.AttendanceDeviceDtos.DeviceEventResponse;
import com.gurukul.attendance.service.AttendanceDeviceEventService;
import com.gurukul.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Attendance Device Events", description = "Hardware-integration endpoint for RFID/fingerprint/face devices. "
		+ "Authenticated by X-Device-Key, not a user JWT - see AttendanceDeviceEventService.")
public class AttendanceDeviceEventController {

	private final AttendanceDeviceEventService attendanceDeviceEventService;

	@PostMapping("/api/v1/attendance/device-events")
	@Operation(summary = "Report a scan event from a registered attendance device",
			description = "Requires X-School-Id (the device's own school) and X-Device-Key (the device's API key, "
					+ "issued once at registration/rotation) headers. Not gated by SecurityConfig role rules - a "
					+ "physical device has no human JWT - the device key itself is the credential.")
	public ApiResponse<DeviceEventResponse> recordEvent(
			@RequestHeader("X-Device-Key") @Parameter(description = "The device's API key") String deviceKey,
			@Valid @RequestBody DeviceEventRequest request) {
		return ApiResponse.success(attendanceDeviceEventService.recordEvent(deviceKey, request), "Attendance marked");
	}

}
