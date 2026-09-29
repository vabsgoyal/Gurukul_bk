package com.gurukul.admissions.service;

/** 409: enrolling would likely create a second record for a child who is already a student. */
public class AdmissionDuplicateException extends RuntimeException {

	public static final String ERROR_CODE = "POSSIBLE_DUPLICATE";

	public AdmissionDuplicateException(String message) {
		super(message);
	}

}
