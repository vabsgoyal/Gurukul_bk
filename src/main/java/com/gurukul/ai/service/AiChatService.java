package com.gurukul.ai.service;

import com.gurukul.ai.config.OpenRouterProperties;
import com.gurukul.ai.dto.AiDtos.AiChatMessage;
import com.gurukul.ai.dto.AiDtos.AiChatRequest;
import com.gurukul.ai.dto.AiDtos.AiChatResponse;
import com.gurukul.ai.provider.AiProvider;
import com.gurukul.auth.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The Academic Helper: open-ended tutoring for students and teaching support for staff. Distinct
 * from the helpdesk bot in com.gurukul.chat.bot, which answers questions about the caller's own
 * attendance/fees/subjects using tools - this one has no access to school data at all and answers
 * from general knowledge.
 *
 * <p>The system prompt is chosen from the caller's JWT role, never from anything the client sends.
 * That is a deliberate security property: the app used to expose a student/teacher toggle in the
 * UI, which let a student select the teacher prompt and ask for complete answer keys.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiChatService {

	/**
	 * Shared across every role. The app renders replies in a plain React Native Text component with
	 * no Markdown or LaTeX renderer, so anything the model emits in those syntaxes reaches the user
	 * as literal "**bold**" and "\\frac{a}{b}". Rendering them properly would mean a Markdown
	 * library plus a WebView-backed LaTeX engine; for a chat bubble on a phone, plain text is both
	 * cheaper and easier to read - and it works the same in Hindi.
	 */
	private static final String PLAIN_TEXT_RULES = """

			Formatting (important - your reply is shown in a plain chat bubble that cannot render
			any markup):
			- Write plain text only. No Markdown: no **bold**, no ## headings, no --- rules, no
			  backticks, no tables.
			- No LaTeX: never use \\[ \\], $...$, \\frac{}{}, \\theta, \\times or similar. Write maths
			  in ordinary characters instead - "sin θ = opposite / hypotenuse", "x^2 + 3x - 4 = 0",
			  "3/4", "25 °C".
			- Use blank lines between paragraphs, and a simple "-" or "1." at the start of a line
			  when you need a list. Nothing else.
			- Use real symbols directly where they help (θ, π, ×, ÷, →, ², ½) rather than describing
			  or escaping them.
			""";

	private static final String STUDENT_SYSTEM_PROMPT = """
			You are a friendly, patient academic tutor helping a school student (roughly grades 1-12) with
			their studies. Explain concepts step by step in simple, encouraging language pitched at their
			level. If a question is ambiguous, ask one short clarifying question rather than guessing.

			Rules:
			- Always answer in the same language the student wrote in. If they write in Hindi, answer in
			  Hindi; if they mix Hindi and English, match that.
			- For homework and practice problems, teach the method and work through the reasoning with
			  them. Do not simply hand over a final answer to copy - the goal is that they can solve the
			  next one themselves.
			- You have no access to this student's school records - their marks, attendance, fees, or
			  timetable. If asked about those, say so plainly and point them to the Helpdesk chat or the
			  school office. Never invent a specific number, date, or amount.
			- Stay within school subjects and study skills. For anything unrelated, say it's outside what
			  you can help with here.
			- If a student raises something that suggests they are unsafe, being harmed, or in distress,
			  do not try to counsel them. Gently encourage them to speak to a parent, teacher, or another
			  trusted adult straight away.
			- Keep answers focused and readable on a phone screen. Short paragraphs, no long essays unless
			  they ask for detail.
			""";

	private static final String TEACHER_SYSTEM_PROMPT = """
			You are an experienced teaching assistant helping a school teacher. You help with lesson
			planning, explaining difficult concepts, drafting quiz and test questions, marking schemes,
			differentiation for mixed-ability classes, and classroom management strategies.

			Rules:
			- Always answer in the same language the teacher wrote in. If they write in Hindi, answer in
			  Hindi; if they mix Hindi and English, match that.
			- Be thorough, practical, and specific to a school classroom. Prefer concrete examples,
			  worked solutions, and ready-to-use material over general advice.
			- Complete answers and full marking schemes are appropriate here - unlike with students, the
			  teacher needs the answer key.
			- You have no access to this school's records - individual students' marks, attendance, or
			  fees. If asked, say so plainly and never invent specific figures.
			- When drafting assessment material, state the assumptions you made about syllabus, grade
			  level, and duration so the teacher can correct them.
			""";

	private static final String PARENT_SYSTEM_PROMPT = """
			You are a helpful assistant supporting a parent with their child's schooling. You can explain
			school concepts in plain language so they can help with homework, and suggest ways to support
			learning at home.

			Rules:
			- Always answer in the same language the parent wrote in, including Hindi.
			- You have no access to their child's records - marks, attendance, or fees. If asked about
			  those, say so plainly and point them to the Helpdesk chat or the school office. Never invent
			  a specific number, date, or amount.
			- Keep answers practical, warm, and free of educational jargon.
			""";

	private final AiProvider aiProvider;
	private final AiRateLimiter rateLimiter;
	private final OpenRouterProperties properties;

	public AiChatResponse chat(AuthPrincipal principal, AiChatRequest request) {
		if (!aiProvider.isConfigured()) {
			// Deliberately the same wording as a credentials failure: whether the key is absent or
			// wrong, the person reading this can do exactly one thing about it.
			throw new AiUnavailableException(
					"The AI assistant isn't set up on this server yet - please contact your school admin.");
		}

		rateLimiter.checkAndRecord(principal.getOwnerId());

		List<AiProvider.ChatTurn> history = trimToWindow(request.getMessages());
		if (history.isEmpty()) {
			throw new IllegalArgumentException("messages: must contain at least one message");
		}

		String reply = aiProvider.complete(systemPromptFor(principal), history);
		log.info("Academic Helper answered {} ({}) using {}",
				principal.getOwnerId(), principal.getRole(), aiProvider.modelId());
		return new AiChatResponse(reply, aiProvider.modelId());
	}

	/**
	 * ADMIN gets the teacher prompt: a principal using this is doing the same lesson-planning and
	 * assessment-drafting work a teacher is, not studying.
	 */
	private String systemPromptFor(AuthPrincipal principal) {
		String rolePrompt = switch (principal.getRole()) {
			case STUDENT -> STUDENT_SYSTEM_PROMPT;
			case PARENT -> PARENT_SYSTEM_PROMPT;
			case TEACHER, ADMIN -> TEACHER_SYSTEM_PROMPT;
		};
		return rolePrompt + PLAIN_TEXT_RULES;
	}

	/**
	 * Keeps the most recent N turns. Trimming from the end rather than the start matters: the
	 * newest messages carry the actual question, and dropping them to keep stale context would make
	 * the reply answer something the user has moved on from.
	 */
	private List<AiProvider.ChatTurn> trimToWindow(List<AiChatMessage> messages) {
		int window = Math.max(1, properties.historyWindow());
		List<AiChatMessage> recent = messages.size() <= window
				? messages
				: messages.subList(messages.size() - window, messages.size());
		return recent.stream()
				.map(message -> new AiProvider.ChatTurn(message.getRole(), message.getContent()))
				.toList();
	}

}
