package com.gurukul.attendance.controller;

import com.gurukul.attendance.dto.AttendanceDeviceDtos.CreateDeviceRequest;
import com.gurukul.attendance.dto.AttendanceDeviceDtos.DeviceKeyResponse;
import com.gurukul.attendance.dto.AttendanceDeviceDtos.DeviceResponse;
import com.gurukul.attendance.dto.AttendanceDeviceDtos.UpdateDeviceRequest;
import com.gurukul.attendance.service.AttendanceDeviceService;
import com.gurukul.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Attendance Devices", description = "Admin management of registered RFID/fingerprint/face devices. Requires X-School-Id header.")
public class AttendanceDeviceController {

	private final AttendanceDeviceService attendanceDeviceService;

	@PostMapping("/api/v1/attendance-devices")
	@Operation(summary = "Register a new attendance device",
			description = "Returns the plaintext API key exactly once - store it on the device now, it cannot be retrieved again (only rotated).")
	public ApiResponse<DeviceKeyResponse> create(@Valid @RequestBody CreateDeviceRequest request) {
		return ApiResponse.success(attendanceDeviceService.create(request), "Device registered");
	}

	@GetMapping("/api/v1/attendance-devices")
	@Operation(summary = "List this school's registered attendance devices")
	public ApiResponse<List<DeviceResponse>> list() {
		return ApiResponse.success(attendanceDeviceService.list());
	}

	@PutMapping("/api/v1/attendance-devices/{deviceId}")
	@Operation(summary = "Rename or activate/deactivate a device")
	public ApiResponse<DeviceResponse> update(@PathVariable UUID deviceId, @Valid @RequestBody UpdateDeviceRequest request) {
		return ApiResponse.success(attendanceDeviceService.update(deviceId, request), "Device updated");
	}

	@PostMapping("/api/v1/attendance-devices/{deviceId}/rotate-key")
	@Operation(summary = "Rotate a device's API key",
			description = "Invalidates the old key immediately and returns the new plaintext key exactly once.")
	public ApiResponse<DeviceKeyResponse> rotateKey(@PathVariable UUID deviceId) {
		return ApiResponse.success(attendanceDeviceService.rotateKey(deviceId), "Key rotated");
	}

}
