package com.gurukul.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class AiDtos {

	/**
	 * The client sends the whole visible conversation on every turn - nothing is persisted
	 * server-side, so a reinstalled app starts a fresh chat. history-window trims this before it
	 * reaches the model, so a long-running conversation can't grow per-request cost without bound.
	 *
	 * <p>Note there is deliberately no "model" or "systemPrompt" field: both are server-side
	 * configuration. A client that could name the model could bill the school for the most
	 * expensive one on the platform, and a client that could set the system prompt could remove
	 * every safety rule in it.
	 */
	@Getter
	@Setter
	@Schema(description = "One turn of an Academic Helper conversation. The full visible history is "
			+ "re-sent each time; the server trims it to app.openrouter.history-window before calling out.")
	public static class AiChatRequest {

		@NotEmpty(message = "must not be empty")
		@Size(max = 100, message = "must contain at most 100 messages")
		@Valid
		private List<AiChatMessage> messages;
	}

	@Getter
	@Setter
	public static class AiChatMessage {

		@NotBlank
		@Pattern(regexp = "user|assistant", message = "must be either \"user\" or \"assistant\"")
		private String role;

		@NotBlank
		@Size(max = 8000, message = "must be at most 8000 characters")
		private String content;
	}

	@Getter
	@AllArgsConstructor
	public static class AiChatResponse {

		@Schema(description = "The assistant's answer, as plain text")
		private String reply;

		@Schema(description = "Which model produced it - useful when the configured model changes "
				+ "between releases, since the model is configuration rather than code")
		private String model;
	}

}
