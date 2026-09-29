package com.gurukul.timetable.service;

/**
 * 409: the change is valid on its own but conflicts with saved timetable data (e.g. removing a
 * period, or switching Saturday off, while slots still use it).
 */
public class TimetableConflictException extends RuntimeException {

	public static final String ERROR_CODE = "TIMETABLE_IN_USE";

	public TimetableConflictException(String message) {
		super(message);
	}

}
