package com.gurukul.schools.service;

/** 409: a registration that looks like a school (or admin) that is already on Gurukul. */
public class SchoolAlreadyRegisteredException extends RuntimeException {

	public static final String ERROR_CODE = "SCHOOL_ALREADY_REGISTERED";

	public SchoolAlreadyRegisteredException(String message) {
		super(message);
	}

}
