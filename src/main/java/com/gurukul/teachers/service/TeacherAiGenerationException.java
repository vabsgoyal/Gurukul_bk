package com.gurukul.teachers.service;

/**
 * Thrown when the AI backend is unreachable/misconfigured, or its response can't be parsed into
 * the expected quiz question shape. Handled by GlobalExceptionHandler as 503 Service Unavailable.
 */
public class TeacherAiGenerationException extends RuntimeException {

	public TeacherAiGenerationException(String message) {
		super(message);
	}

	public TeacherAiGenerationException(String message, Throwable cause) {
		super(message, cause);
	}

}
