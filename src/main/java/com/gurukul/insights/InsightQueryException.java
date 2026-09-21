package com.gurukul.insights;

/**
 * Carries a message safe to show the bot's LLM turn directly (e.g. "unknown filter field", or a
 * clarifying question like "multiple employees match 'Ravi' - which one?"). Callers in the chat/bot
 * tool layer catch this specifically and return it as a normal (non-error) tool result so the model
 * can read and act on it, rather than letting it fall into BotReplyService's generic
 * swallowed-exception fallback message.
 */
public class InsightQueryException extends RuntimeException {

	public InsightQueryException(String message) {
		super(message);
	}

}
