package com.gurukul.attendance.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "How an attendance mark or a device/identifier authenticates a person")
public enum AttendanceMethod {

	@Schema(description = "RFID card/tag reader")
	RFID,

	@Schema(description = "Fingerprint/biometric scanner")
	FINGERPRINT,

	@Schema(description = "Face-recognition kiosk - matching happens on the vendor's device/SDK, never in this backend")
	FACE

}
