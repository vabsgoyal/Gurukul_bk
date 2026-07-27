package com.gurukul.chat.bot.tool;

import com.gurukul.auth.security.AuthPrincipal;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class BotToolRegistry {

	private final List<BotTool> tools;

	public BotToolRegistry(List<BotTool> tools) {
		this.tools = tools;
	}

	public List<BotTool> toolsFor(AuthPrincipal principal) {
		return tools.stream().filter(tool -> tool.appliesTo(principal)).toList();
	}

	public Optional<BotTool> byName(String name) {
		return tools.stream().filter(tool -> tool.name().equals(name)).findFirst();
	}

}
