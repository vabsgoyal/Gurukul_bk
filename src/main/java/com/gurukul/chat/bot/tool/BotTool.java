package com.gurukul.chat.bot.tool;

import com.anthropic.models.messages.Tool;
import com.gurukul.auth.security.AuthPrincipal;

import java.util.Map;

/**
 * Every implementation MUST derive "whose data" from the passed-in AuthPrincipal, never from
 * {@code input} - a prompt-injected or hallucinated id in the LLM's tool call must be ignored.
 * Callers (BotReplyService) wrap {@link #execute} in PrincipalContextRunner.runAs, since these
 * tools call into existing domain services that internally read AuthContext/SchoolContext
 * ThreadLocals never populated on a STOMP message-handling thread.
 */
public interface BotTool {

	String name();

	String description();

	Tool.InputSchema inputSchema();

	/** Filters the tool set per caller, e.g. a student is never offered a staff-only tool. */
	boolean appliesTo(AuthPrincipal principal);

	Object execute(AuthPrincipal principal, Map<String, Object> input);

}
