package com.gurukul.ai.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gurukul.ai.config.OpenRouterProperties;
import com.gurukul.ai.service.AiUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;

/**
 * AiProvider backed by OpenRouter's OpenAI-compatible POST /chat/completions.
 *
 * <p>Knows the wire format and the failure mapping, and nothing else - no school concepts, no
 * prompts, no roles. Because the shape is the OpenAI-compatible one, the same class would talk to
 * OpenAI, Groq, Together, or DeepSeek directly by changing app.openrouter.base-url; the provider is
 * not baked into the code any more than the model is.
 *
 * <p>Every upstream failure becomes an AiUnavailableException whose message is already safe to show
 * a student. Nothing here lets a provider error, stack trace, or response body reach the client -
 * the same rule BotReplyService follows for the helpdesk bot, and for the same reason: a provider
 * hiccup must not surface as a 500 or leak internals.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpenRouterAiProvider implements AiProvider {

	private static final String BUSY = "The assistant is busy right now - please try again in a moment.";
	private static final String MISCONFIGURED =
			"The AI assistant isn't set up correctly on this server - please contact your school admin.";
	private static final String TROUBLE = "Sorry, I'm having trouble right now - please try again shortly.";

	private final RestClient openRouterRestClient;
	private final OpenRouterProperties properties;

	@Override
	public boolean isConfigured() {
		return properties.isConfigured();
	}

	@Override
	public String modelId() {
		return properties.model();
	}

	@Override
	public String complete(String systemPrompt, List<ChatTurn> history) {
		List<ChatTurn> messages = new ArrayList<>(history.size() + 1);
		messages.add(new ChatTurn("system", systemPrompt));
		messages.addAll(history);

		CompletionRequest request = new CompletionRequest(
				properties.model(), messages, properties.maxOutputTokens(), properties.temperature());

		CompletionResponse response;
		try {
			response = openRouterRestClient.post()
					.uri("/chat/completions")
					.body(request)
					.retrieve()
					.body(CompletionResponse.class);
		} catch (RestClientResponseException ex) {
			throw new AiUnavailableException(messageForStatus(ex));
		} catch (ResourceAccessException ex) {
			// Connect/read timeout, DNS failure, socket reset - either it never reached the
			// provider or the provider took too long. Retrying is the right advice.
			log.warn("OpenRouter unreachable or timed out", ex);
			throw new AiUnavailableException(TROUBLE);
		} catch (Exception ex) {
			log.error("Unexpected error calling OpenRouter", ex);
			throw new AiUnavailableException(TROUBLE);
		}

		String reply = firstReplyText(response);
		if (reply == null || reply.isBlank()) {
			// A 200 carrying no usable text. Most often a reasoning model (the deepseek-r1 family,
			// for instance) that puts its answer in a separate field and leaves content empty, or
			// upstream content filtering. Not fixed by retrying, so say something actionable.
			log.warn("OpenRouter returned no usable content for model {}", properties.model());
			throw new AiUnavailableException(
					"The assistant didn't return an answer - please rephrase your question and try again.");
		}
		return reply;
	}

	private String messageForStatus(RestClientResponseException ex) {
		int status = ex.getStatusCode().value();
		// The response body can echo request content back, so it is logged and never returned.
		switch (status) {
			case 401, 403 -> {
				log.error("OpenRouter rejected our credentials ({}) - check OPENROUTER_API_KEY", status);
				return MISCONFIGURED;
			}
			case 402 -> {
				// Out of credit, or this key's own credit limit is spent. An admin must act;
				// retrying will not help, so don't tell the user to try again.
				log.error("OpenRouter reports insufficient credit (402) - top up, or raise the key's credit limit");
				return "The AI assistant is temporarily unavailable - please contact your school admin.";
			}
			case 429 -> {
				log.warn("OpenRouter rate limited us (429)");
				return BUSY;
			}
			case 400, 404 -> {
				// Nearly always our own doing: a retired or misspelled model slug, or a payload the
				// provider rejected. Reported as "misconfigured" because the user cannot fix it.
				log.error("OpenRouter rejected the request ({}) for model {} - body: {}",
						status, properties.model(), ex.getResponseBodyAsString());
				return MISCONFIGURED;
			}
			default -> {
				log.error("OpenRouter returned {} - body: {}", status, ex.getResponseBodyAsString());
				return TROUBLE;
			}
		}
	}

	private String firstReplyText(CompletionResponse response) {
		if (response == null || response.choices() == null || response.choices().isEmpty()) {
			return null;
		}
		CompletionResponse.Choice choice = response.choices().get(0);
		return choice == null || choice.message() == null ? null : choice.message().content();
	}

	private record CompletionRequest(
			String model,
			List<ChatTurn> messages,
			@JsonProperty("max_tokens") int maxTokens,
			double temperature) {
	}

	/**
	 * Ignores unknown fields deliberately: OpenRouter passes through provider-specific extras
	 * (usage accounting, reasoning blocks, provider metadata) that differ per model and change over
	 * time, and none of them should be able to break a working deployment.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	private record CompletionResponse(String id, String model, List<Choice> choices) {

		@JsonIgnoreProperties(ignoreUnknown = true)
		private record Choice(Message message, @JsonProperty("finish_reason") String finishReason) {
		}

		@JsonIgnoreProperties(ignoreUnknown = true)
		private record Message(String role, String content) {
		}
	}

}
