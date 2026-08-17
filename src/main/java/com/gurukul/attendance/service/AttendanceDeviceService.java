package com.gurukul.attendance.service;

import com.gurukul.attendance.dto.AttendanceDeviceDtos.CreateDeviceRequest;
import com.gurukul.attendance.dto.AttendanceDeviceDtos.DeviceKeyResponse;
import com.gurukul.attendance.dto.AttendanceDeviceDtos.DeviceResponse;
import com.gurukul.attendance.dto.AttendanceDeviceDtos.UpdateDeviceRequest;
import com.gurukul.attendance.entity.AttendanceDevice;
import com.gurukul.attendance.repository.AttendanceDeviceRepository;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Admin-facing CRUD for registered attendance devices. The plaintext API key is generated here,
 * shown to the caller exactly once (create/rotate response), and never stored or logged - only its
 * BCrypt hash is persisted. AttendanceDeviceEventService is the only other reader of this table, and
 * only ever compares an incoming key against the stored hash, never reads/decodes it back.
 */
@Service
@RequiredArgsConstructor
public class AttendanceDeviceService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final AttendanceDeviceRepository attendanceDeviceRepository;
	private final PasswordEncoder passwordEncoder;
	private final SchoolContext schoolContext;

	@Transactional
	public DeviceKeyResponse create(CreateDeviceRequest request) {
		AttendanceDevice device = new AttendanceDevice();
		device.setSchoolId(schoolContext.getSchoolId());
		device.setName(request.getName());
		device.setDeviceType(request.getDeviceType());
		device.setActive(true);
		String apiKey = issueKey(device);
		AttendanceDevice saved = attendanceDeviceRepository.save(device);
		return new DeviceKeyResponse(saved.getId(), saved.getName(), saved.getDeviceType(), apiKey);
	}

	@Transactional(readOnly = true)
	public List<DeviceResponse> list() {
		return attendanceDeviceRepository.findAllBySchoolIdOrderByNameAsc(schoolContext.getSchoolId()).stream()
				.map(DeviceResponse::from)
				.toList();
	}

	@Transactional
	public DeviceResponse update(UUID deviceId, UpdateDeviceRequest request) {
		AttendanceDevice device = getScopedEntity(deviceId);
		device.setName(request.getName());
		device.setActive(request.getActive());
		return DeviceResponse.from(attendanceDeviceRepository.save(device));
	}

	@Transactional
	public DeviceKeyResponse rotateKey(UUID deviceId) {
		AttendanceDevice device = getScopedEntity(deviceId);
		String apiKey = issueKey(device);
		AttendanceDevice saved = attendanceDeviceRepository.save(device);
		return new DeviceKeyResponse(saved.getId(), saved.getName(), saved.getDeviceType(), apiKey);
	}

	AttendanceDevice getScopedEntity(UUID deviceId) {
		return attendanceDeviceRepository.findByIdAndSchoolId(deviceId, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Attendance device not found"));
	}

	private String issueKey(AttendanceDevice device) {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String apiKey = "adk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		device.setApiKeyHash(passwordEncoder.encode(apiKey));
		return apiKey;
	}

}
