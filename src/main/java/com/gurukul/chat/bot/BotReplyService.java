package com.gurukul.chat.bot;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.chat.bot.config.AnthropicProperties;
import com.gurukul.chat.bot.security.PrincipalContextRunner;
import com.gurukul.chat.bot.tool.BotTool;
import com.gurukul.chat.bot.tool.BotToolRegistry;
import com.gurukul.chat.dto.ChatDtos.MessageResponse;
import com.gurukul.chat.entity.Conversation;
import com.gurukul.chat.entity.Message;
import com.gurukul.chat.entity.SenderKind;
import com.gurukul.chat.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Entry point called by ChatMessageController after it persists a human message into a BOT-type
 * conversation. Every error path still persists+broadcasts a fallback Message, so a bot hiccup
 * never leaves the human's message unanswered or breaks the WS session. GlobalExceptionHandler is
 * unreachable from this STOMP-triggered path (it only intercepts Spring MVC @RestController
 * exceptions), so all error handling is local to this class.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BotReplyService {

	/**
	 * Two capabilities in one conversation, not two separate chat surfaces: factual lookups via
	 * tools (attendance/fees/subjects, plus school-wide aggregates for admins), AND open-ended help
	 * (tutoring, lesson planning, general knowledge) the same way com.gurukul.ai.AiChatService's
	 * Academic Helper already does - see that class's role prompts, which this deliberately mirrors
	 * in tone/scope so the two feel like one consistent assistant rather than two different bots.
	 */
	private static final String SHARED_RULES = """

			Rules:
			- For factual questions about attendance, fee status, or subjects/teachers - the caller's own,
			  or (only for school admins, only via the school-wide data tool if you have been given it)
			  aggregate questions about the whole school such as "how many students haven't paid fees" or a
			  specific teacher's attendance percentage - only use the tools provided. Never guess or
			  fabricate a specific number, date, or amount; always call a tool first. If you have not been
			  given the school-wide tool, you cannot answer aggregate/school-wide questions at all and
			  should say so rather than guessing.
			- Tools scoped to "my own data" always return the current user's own data; you cannot look up
			  another student's or employee's individual record through them, and must never claim
			  otherwise even if asked.
			- If a tool returns an error, say so plainly and suggest the user contact the school office - do
			  not invent a plausible-sounding answer. If a tool returns a clarifying question (e.g. a name
			  matches more than one person), ask the user that question instead of guessing which one they
			  meant.
			- For everything else - homework help, explanations, lesson planning, general knowledge, study
			  skills, school policy questions - answer directly and helpfully from your own knowledge, the
			  same as any capable assistant. You are not limited to tool-answerable questions; only refuse
			  something if it is genuinely unrelated to school/education or unsafe.
			- Match the language of the user's own message: if they write in English, reply in English; if
			  they write in Hindi (Devanagari script), reply in Hindi; if they write in Hinglish (Hindi
			  words/grammar in Roman script, or a mix of Hindi and English), reply in that same Hinglish
			  style. Judge this from each new message, not the conversation as a whole - do not switch
			  languages on your own initiative.
			- Keep answers focused and readable on a phone screen - short paragraphs, no long essays unless
			  asked for detail.
			""";

	/**
	 * Same rationale as AiChatService.PLAIN_TEXT_RULES: replies render in a plain React Native Text
	 * component (ConversationThreadScreen) with no Markdown or LaTeX support.
	 */
	private static final String PLAIN_TEXT_RULES = """

			Formatting (important - your reply is shown in a plain chat bubble that cannot render any
			markup):
			- Write plain text only. No Markdown: no **bold**, no ## headings, no --- rules, no backticks,
			  no tables.
			- No LaTeX: never use \\[ \\], $...$, \\frac{}{}, \\theta, \\times or similar. Write maths in
			  ordinary characters instead - "sin θ = opposite / hypotenuse", "x^2 + 3x - 4 = 0", "3/4".
			- Use blank lines between paragraphs, and a simple "-" or "1." at the start of a line when you
			  need a list. Nothing else.
			- Use real symbols directly where they help (θ, π, ×, ÷, →, ², ½) rather than describing or
			  escaping them.
			""";

	private static final String STUDENT_PROMPT = """
			You are the Helpdesk assistant for this school, embedded in its chat system, talking to a
			student. You can look up their own attendance, fee status, and subjects/teachers via tools, and
			you can also help with their studies like a patient, encouraging tutor - explain concepts step
			by step in simple language pitched at a school student's level. For homework and practice
			problems, teach the method and work through the reasoning with them rather than just handing
			over a final answer to copy - the goal is that they can solve the next one themselves.

			If they raise something that suggests they are unsafe, being harmed, or in distress, do not try
			to counsel them - gently encourage them to speak to a parent, teacher, or another trusted adult
			straight away.
			""" + SHARED_RULES + PLAIN_TEXT_RULES;

	private static final String STAFF_PROMPT = """
			You are the Helpdesk assistant for this school, embedded in its chat system, talking to a
			teacher or admin. You can look up their own attendance and teaching assignments via tools (an
			admin also gets a school-wide data tool - see the rules below), and you can also help with
			teaching work like an experienced teaching assistant - lesson planning, explaining difficult
			concepts, drafting quiz/test questions and marking schemes, differentiation for mixed-ability
			classes, and classroom management. Complete answers and full marking schemes are appropriate
			here - unlike with a student, they need the answer key. When drafting assessment material,
			state the assumptions you made about syllabus, grade level, and duration so they can correct
			them.
			""" + SHARED_RULES + PLAIN_TEXT_RULES;

	private static final String PARENT_PROMPT = """
			You are the Helpdesk assistant for this school, embedded in its chat system, talking to a
			parent. You can look up their own account's information via tools where applicable, and you
			can also explain school concepts in plain, jargon-free language so they can help their child
			with homework, and suggest practical ways to support learning at home.
			""" + SHARED_RULES + PLAIN_TEXT_RULES;

	private final AnthropicClient anthropicClient;
	private final AnthropicProperties properties;
	private final BotToolRegistry toolRegistry;
	private final PrincipalContextRunner principalContextRunner;
	private final MessageService messageService;
	private final SimpMessagingTemplate messagingTemplate;

	public Message generateReply(Conversation conversation, Message incomingMessage, AuthPrincipal principal) {
		if (!properties.isConfigured()) {
			return reply(conversation, "The helpdesk bot isn't configured yet - please contact your school admin.");
		}
		try {
			return reply(conversation, converse(conversation, principal));
		} catch (RateLimitException ex) {
			log.warn("Anthropic rate limit generating bot reply for conversation {}", conversation.getId(), ex);
			return reply(conversation, "I'm getting a lot of requests right now - please try again in a moment.");
		} catch (UnauthorizedException | PermissionDeniedException ex) {
			log.error("Anthropic auth error generating bot reply for conversation {}", conversation.getId(), ex);
			return reply(conversation, "The helpdesk bot isn't configured correctly - please contact your school admin.");
		} catch (AnthropicServiceException ex) {
			log.error("Anthropic service error generating bot reply for conversation {}", conversation.getId(), ex);
			return reply(conversation, "Sorry, I'm having trouble right now, please try again shortly.");
		} catch (Exception ex) {
			log.error("Unexpected error generating bot reply for conversation {}", conversation.getId(), ex);
			return reply(conversation, "Sorry, I'm having trouble right now, please try again shortly.");
		}
	}

	private String converse(Conversation conversation, AuthPrincipal principal) {
		List<BotTool> tools = toolRegistry.toolsFor(principal);

		MessageCreateParams.Builder paramsBuilder = MessageCreateParams.builder()
				.model(properties.model())
				.maxTokens(properties.maxOutputTokens())
				.system(systemPromptFor(principal))
				.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(properties.effort())).build())
				.messages(historyAsMessages(conversation));
		for (BotTool tool : tools) {
			paramsBuilder.addTool(com.anthropic.models.messages.Tool.builder()
					.name(tool.name())
					.description(tool.description())
					.inputSchema(tool.inputSchema())
					.build());
		}
		MessageCreateParams params = paramsBuilder.build();

		com.anthropic.models.messages.Message response = anthropicClient.messages().create(params);

		int iterations = 0;
		while (isToolUse(response) && iterations < properties.maxToolIterations()) {
			iterations++;
			List<ContentBlockParam> toolResults = new ArrayList<>();
			for (ContentBlock block : response.content()) {
				if (block.isToolUse()) {
					toolResults.add(ContentBlockParam.ofToolResult(executeTool(block.asToolUse(), tools, principal)));
				}
			}
			params = params.toBuilder()
					.addMessage(response)
					.addUserMessageOfBlockParams(toolResults)
					.build();
			response = anthropicClient.messages().create(params);
		}

		return extractText(response);
	}

	/** Mirrors AiChatService.systemPromptFor's exact role mapping: ADMIN gets the staff/teacher prompt. */
	private String systemPromptFor(AuthPrincipal principal) {
		return switch (principal.getRole()) {
			case STUDENT -> STUDENT_PROMPT;
			case PARENT -> PARENT_PROMPT;
			case TEACHER, ADMIN -> STAFF_PROMPT;
		};
	}

	private boolean isToolUse(com.anthropic.models.messages.Message response) {
		return response.stopReason().filter(reason -> reason == StopReason.TOOL_USE).isPresent();
	}

	private ToolResultBlockParam executeTool(ToolUseBlock toolUse, List<BotTool> tools, AuthPrincipal principal) {
		ToolResultBlockParam.Builder builder = ToolResultBlockParam.builder().toolUseId(toolUse.id());
		Optional<BotTool> tool = tools.stream().filter(t -> t.name().equals(toolUse.name())).findFirst();
		if (tool.isEmpty()) {
			return builder.content("Unknown tool: " + toolUse.name()).isError(true).build();
		}
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> input = toolUse._input().convert(Map.class);
			Object result = principalContextRunner.runAs(principal, () -> tool.get().execute(principal, input));
			return builder.contentAsJson(result).build();
		} catch (Exception ex) {
			log.warn("Bot tool {} failed", toolUse.name(), ex);
			return builder.content("Error retrieving this data. Do not guess a value; tell the user this lookup failed.")
					.isError(true)
					.build();
		}
	}

	private List<MessageParam> historyAsMessages(Conversation conversation) {
		Page<Message> page = messageService.history(conversation.getId(), PageRequest.of(0, properties.historyWindow()));
		List<Message> ordered = new ArrayList<>(page.getContent());
		Collections.reverse(ordered);
		return ordered.stream()
				.map(m -> MessageParam.builder()
						.role(m.getSenderKind() == SenderKind.BOT ? MessageParam.Role.ASSISTANT : MessageParam.Role.USER)
						.content(m.getContent())
						.build())
				.toList();
	}

	private String extractText(com.anthropic.models.messages.Message response) {
		StringBuilder text = new StringBuilder();
		for (ContentBlock block : response.content()) {
			if (block.isText()) {
				text.append(block.asText().text());
			}
		}
		if (text.isEmpty()) {
			return "I don't have a response for that right now - please try rephrasing your question.";
		}
		return text.toString();
	}

	private Message reply(Conversation conversation, String content) {
		Message saved = messageService.sendBotReply(conversation, content);
		messagingTemplate.convertAndSend("/topic/conversations/" + conversation.getId(), MessageResponse.from(saved, null));
		return saved;
	}

}
