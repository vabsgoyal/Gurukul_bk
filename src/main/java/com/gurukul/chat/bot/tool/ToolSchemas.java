package com.gurukul.chat.bot.tool;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;

import java.util.List;
import java.util.Map;

final class ToolSchemas {

	private ToolSchemas() {
	}

	static Tool.InputSchema empty() {
		return Tool.InputSchema.builder()
				.properties(Tool.InputSchema.Properties.builder().build())
				.required(List.of())
				.build();
	}

	static Tool.InputSchema optionalDateRange() {
		return Tool.InputSchema.builder()
				.properties(Tool.InputSchema.Properties.builder()
						.putAdditionalProperty("from", JsonValue.from(Map.of(
								"type", "string",
								"description", "ISO-8601 date, inclusive start. Omit for full history.")))
						.putAdditionalProperty("to", JsonValue.from(Map.of(
								"type", "string",
								"description", "ISO-8601 date, inclusive end. Omit for full history.")))
						.build())
				.required(List.of())
				.build();
	}

}
