package com.gurukul.ai.service;

/**
 * Every way the assistant can fail to answer - unconfigured, rate-limited, upstream down - is
 * funnelled through this one exception carrying a message that is already safe to show a student or
 * teacher. GlobalExceptionHandler maps it to 503 so the app can tell "assistant is having trouble"
 * apart from "your request was invalid" without parsing prose.
 *
 * <p>Never put an upstream error body in the message: it can echo request content back, and the
 * OpenRouter response body has no reason to reach a phone. Log that side separately.
 */
public class AiUnavailableException extends RuntimeException {

	public static final String ERROR_CODE = "AI_UNAVAILABLE";

	public AiUnavailableException(String message) {
		super(message);
	}

}
