package com.gurukul.timetable.service;

import com.gurukul.timetable.dto.TimetableDtos.ClashResponse;
import lombok.Getter;

import java.util.List;

/** 409: a teacher in the submitted timetable is already teaching another section at that time. */
@Getter
public class TimetableClashException extends RuntimeException {

	public static final String ERROR_CODE = "TIMETABLE_CLASH";

	private final transient List<ClashResponse> clashes;

	public TimetableClashException(String message, List<ClashResponse> clashes) {
		super(message);
		this.clashes = clashes;
	}

}
