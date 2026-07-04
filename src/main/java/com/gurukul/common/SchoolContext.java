package com.gurukul.common;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SchoolContext {

	private static final ThreadLocal<UUID> CURRENT_SCHOOL_ID = new ThreadLocal<>();

	public void setSchoolId(UUID schoolId) {
		CURRENT_SCHOOL_ID.set(schoolId);
	}

	public UUID getSchoolId() {
		UUID schoolId = CURRENT_SCHOOL_ID.get();
		if (schoolId == null) {
			throw new MissingSchoolIdException("Missing X-School-Id header");
		}
		return schoolId;
	}

	public void clear() {
		CURRENT_SCHOOL_ID.remove();
	}

}
