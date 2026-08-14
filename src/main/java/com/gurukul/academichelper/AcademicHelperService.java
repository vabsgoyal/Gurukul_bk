package com.gurukul.academichelper;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.gurukul.academichelper.AcademicHelperDtos.AskRequest;
import com.gurukul.academichelper.AcademicHelperDtos.ChatMessageDto;
import com.gurukul.chat.bot.config.AnthropicProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Server-side proxy for the "Academic Helper" tutor/teaching-assistant chat, replacing a previous
 * client-side call straight to AWS Bedrock that shipped an AWS access key/secret inside the app
 * binary (docs/security/SECURITY_AND_ACCESS.md SS9.3). Reuses the AnthropicClient bean already wired
 * up for the Helpdesk bot (com.gurukul.chat.bot) - same app.anthropic.backend=bedrock switch, no new
 * dependency. Unlike the Helpdesk bot, there is no tool use and no persisted conversation - the
 * frontend keeps the transcript in local state and resends the full history each turn.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AcademicHelperService {

	private static final String STUDENT_SYSTEM_PROMPT = """
			You are a friendly, patient academic tutor helping a school student (grades K-12) understand \
			concepts and solve homework problems. Explain step by step in simple, encouraging language \
			appropriate for their level. If the question is ambiguous, ask a clarifying question. Always \
			answer in the same language the student's question is written in.""";

	private static final String TEACHER_SYSTEM_PROMPT = """
			You are an expert teaching assistant helping a school teacher with lesson planning, pedagogy, \
			explaining difficult concepts, drafting quiz/test questions, and classroom management \
			strategies. Be thorough, professional, and practical. Always answer in the same language the \
			teacher's question is written in.""";

	private final AnthropicClient anthropicClient;
	private final AnthropicProperties properties;

	public String ask(AskRequest request) {
		if (!properties.isConfigured()) {
			throw new IllegalStateException("The Academic Helper isn't configured yet - please contact your school admin.");
		}

		String systemPrompt = "teacher".equals(request.mode()) ? TEACHER_SYSTEM_PROMPT : STUDENT_SYSTEM_PROMPT;

		MessageCreateParams params = MessageCreateParams.builder()
				.model(properties.model())
				.maxTokens(properties.maxOutputTokens())
				.system(systemPrompt)
				.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(properties.effort())).build())
				.messages(request.messages().stream().map(this::toMessageParam).toList())
				.build();

		try {
			return extractText(anthropicClient.messages().create(params));
		} catch (RateLimitException ex) {
			log.warn("Anthropic rate limit generating academic helper reply", ex);
			throw new IllegalStateException("The Academic Helper is getting a lot of requests right now - please try again in a moment.");
		} catch (UnauthorizedException | PermissionDeniedException ex) {
			log.error("Anthropic auth error generating academic helper reply", ex);
			throw new IllegalStateException("The Academic Helper isn't configured correctly - please contact your school admin.");
		} catch (AnthropicServiceException ex) {
			log.error("Anthropic service error generating academic helper reply", ex);
			throw new IllegalStateException("Sorry, the Academic Helper is having trouble right now, please try again shortly.");
		}
	}

	private MessageParam toMessageParam(ChatMessageDto message) {
		return MessageParam.builder()
				.role("assistant".equals(message.role()) ? MessageParam.Role.ASSISTANT : MessageParam.Role.USER)
				.content(message.content())
				.build();
	}

	private String extractText(Message response) {
		StringBuilder text = new StringBuilder();
		for (ContentBlock block : response.content()) {
			if (block.isText()) {
				text.append(block.asText().text());
			}
		}
		if (text.isEmpty()) {
			throw new IllegalStateException("The Academic Helper returned an empty response.");
		}
		return text.toString();
	}

}
