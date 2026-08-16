package com.gurukul.ai.provider;

import java.util.List;

/**
 * One way of getting an answer out of a large language model. AiChatService owns the prompts, the
 * role rules, and the rate limiting; an implementation of this owns nothing but "send these turns,
 * give me back text".
 *
 * <p>This exists so the provider can change without the feature changing. Today the only
 * implementation is OpenRouter (an API key, any model, cheap to test against). A Bedrock
 * implementation would authenticate with the EC2 instance role instead - no key stored anywhere -
 * and slot in behind the same interface, selected by app.ai.provider. That mirrors the switch
 * AnthropicClientConfig already offers the helpdesk bot via app.anthropic.backend.
 *
 * <p>Implementations must never let a provider failure escape as anything other than
 * AiUnavailableException, and must never put an upstream response body into its message - that body
 * can echo the user's own prompt back and has no business reaching a phone.
 */
public interface AiProvider {

	/** role is "user" or "assistant"; the system prompt is passed separately. */
	record ChatTurn(String role, String content) {
	}

	/**
	 * False when this provider has no usable credentials. Checked before every call so a missing
	 * key produces a readable message rather than a failed request, and so app startup never
	 * depends on a secret being present.
	 */
	boolean isConfigured();

	/** The model identifier this provider will use, for display and logging. */
	String modelId();

	/**
	 * @throws com.gurukul.ai.service.AiUnavailableException on any failure, always carrying a
	 *         message that is already safe to show a student or teacher.
	 */
	String complete(String systemPrompt, List<ChatTurn> history);

}
