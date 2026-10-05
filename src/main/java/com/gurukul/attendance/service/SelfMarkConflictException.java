package com.gurukul.attendance.service;

/**
 * 409: a staff self check-in tried to replace today's attendance that an admin already entered
 * (e.g. ABSENT or LATE). Only an admin can change an admin-entered record.
 */
public class SelfMarkConflictException extends RuntimeException {

	public static final String ERROR_CODE = "ATTENDANCE_SET_BY_ADMIN";

	public SelfMarkConflictException(String message) {
		super(message);
	}

}
