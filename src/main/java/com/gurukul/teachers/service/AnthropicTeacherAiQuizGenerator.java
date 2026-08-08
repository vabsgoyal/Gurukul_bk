package com.gurukul.teachers.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurukul.chat.bot.config.AnthropicProperties;
import com.gurukul.common.SchoolContext;
import com.gurukul.students.entity.ClassSection;
import com.gurukul.teachers.dto.AiQuizGenerationRequest;
import com.gurukul.teachers.dto.AiQuizGenerationResponse;
import com.gurukul.teachers.dto.GeneratedQuizQuestionResponse;
import com.gurukul.teachers.entity.QuestionType;
import com.gurukul.teachers.entity.Teacher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Model-backed replacement for {@link LocalTeacherAiQuizGenerator}, calling the same
 * AnthropicClient bean BotReplyService uses (see AnthropicClientConfig - backend is "direct" or
 * "bedrock" depending on app.anthropic.backend). Question order/type and marks are decided by us,
 * not the model, so the response shape is always deterministic even though the content isn't -
 * the model only supplies question/options/answer/explanation text per slot.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.teacher-ai", name = "quiz-generator", havingValue = "bedrock")
public class AnthropicTeacherAiQuizGenerator implements TeacherAiQuizGenerator {

	private static final List<QuestionType> DEFAULT_TYPES = List.of(QuestionType.MCQ, QuestionType.SHORT_ANSWER);
	private static final long GENERATION_MAX_TOKENS = 4096;

	private static final String SYSTEM_PROMPT = """
			You are an expert school test-paper setter. Given a class, subject, syllabus, and
			difficulty level, you write clear, age-appropriate quiz/test questions.

			Rules:
			- Respond with ONLY a raw JSON array - no markdown code fences, no prose before or after.
			- The array must have exactly as many objects as the number of questions requested, in
			  the same order the questions are listed.
			- Each object must have exactly these fields: "question" (string), "options" (array of
			  strings - only for MCQ or TRUE_FALSE questions, empty array otherwise), "answer"
			  (string - the correct answer or a model answer), "explanation" (string - one sentence
			  on what the question checks or why the answer is correct).
			- Do not include marks, question numbers, or question type in the JSON - those are
			  tracked separately.
			""";

	private final AnthropicClient anthropicClient;
	private final AnthropicProperties properties;
	private final SchoolContext schoolContext;
	private final ObjectMapper objectMapper;

	@Override
	public AiQuizGenerationResponse generate(
			Teacher teacher,
			ClassSection classSection,
			AiQuizGenerationRequest request) {
		List<QuestionType> questionTypes = normalizeQuestionTypes(request.getQuestionTypes());
		String userPrompt = buildUserPrompt(classSection, request, questionTypes);

		MessageCreateParams params = MessageCreateParams.builder()
				.model(properties.model())
				.maxTokens(GENERATION_MAX_TOKENS)
				.system(SYSTEM_PROMPT)
				.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(properties.effort())).build())
				.addUserMessage(userPrompt)
				.build();

		Message response = callModel(params);
		List<RawQuestion> rawQuestions = parseQuestions(extractText(response));
		if (rawQuestions.size() != request.getQuestionCount()) {
			throw new TeacherAiGenerationException(
					"The AI returned " + rawQuestions.size() + " questions but "
							+ request.getQuestionCount() + " were requested. Please try again.");
		}

		List<Integer> marks = QuizMarksAllocator.distribute(request.getMaxMarks(), request.getQuestionCount());
		List<GeneratedQuizQuestionResponse> questions = new ArrayList<>(rawQuestions.size());
		for (int i = 0; i < rawQuestions.size(); i++) {
			RawQuestion raw = rawQuestions.get(i);
			QuestionType type = questionTypes.get(i % questionTypes.size());
			List<String> options = raw.options() != null ? raw.options() : List.of();
			questions.add(new GeneratedQuizQuestionResponse(
					i + 1, type, raw.question(), options, raw.answer(), raw.explanation(), marks.get(i)));
		}

		return new AiQuizGenerationResponse(
				schoolContext.getSchoolId(),
				teacher.getId(),
				teacher.getName(),
				classSection.getId(),
				classSection.getDisplayLabel(),
				request.getSubjectName(),
				request.getAssessmentType(),
				request.getTitle(),
				request.getSyllabus(),
				request.getDifficulty(),
				request.getMaxMarks(),
				request.getQuestionCount(),
				"ANTHROPIC_BEDROCK",
				"AI-generated draft - review for accuracy before publishing to students.",
				questions);
	}

	private Message callModel(MessageCreateParams params) {
		try {
			return anthropicClient.messages().create(params);
		} catch (RateLimitException ex) {
			log.warn("Anthropic rate limit generating teacher quiz", ex);
			throw new TeacherAiGenerationException(
					"The AI quiz generator is receiving a lot of requests right now. Please try again in a moment.", ex);
		} catch (UnauthorizedException | PermissionDeniedException ex) {
			log.error("Anthropic auth error generating teacher quiz", ex);
			throw new TeacherAiGenerationException(
					"The AI quiz generator isn't configured correctly. Please contact your school admin.", ex);
		} catch (AnthropicServiceException ex) {
			log.error("Anthropic service error generating teacher quiz", ex);
			throw new TeacherAiGenerationException("The AI quiz generator is temporarily unavailable. Please try again shortly.", ex);
		}
	}

	private String buildUserPrompt(
			ClassSection classSection,
			AiQuizGenerationRequest request,
			List<QuestionType> questionTypes) {
		StringBuilder prompt = new StringBuilder();
		prompt.append("Class: ").append(classSection.getDisplayLabel()).append('\n');
		prompt.append("Subject: ").append(request.getSubjectName()).append('\n');
		prompt.append("Assessment type: ").append(request.getAssessmentType()).append('\n');
		prompt.append("Title: ").append(request.getTitle()).append('\n');
		prompt.append("Syllabus / topics: ").append(request.getSyllabus()).append('\n');
		prompt.append("Difficulty: ").append(request.getDifficulty()).append('\n');
		if (request.getAdditionalInstructions() != null && !request.getAdditionalInstructions().isBlank()) {
			prompt.append("Additional instructions: ").append(request.getAdditionalInstructions()).append('\n');
		}
		prompt.append("Generate exactly ").append(request.getQuestionCount())
				.append(" questions, in this exact order and type:\n");
		for (int i = 0; i < request.getQuestionCount(); i++) {
			QuestionType type = questionTypes.get(i % questionTypes.size());
			prompt.append("Question ").append(i + 1).append(": type ").append(type).append('\n');
		}
		return prompt.toString();
	}

	private String extractText(Message response) {
		StringBuilder text = new StringBuilder();
		for (ContentBlock block : response.content()) {
			if (block.isText()) {
				text.append(block.asText().text());
			}
		}
		if (text.isEmpty()) {
			throw new TeacherAiGenerationException("The AI quiz generator returned an empty response. Please try again.");
		}
		return text.toString();
	}

	private List<RawQuestion> parseQuestions(String rawText) {
		String cleaned = stripCodeFences(rawText);
		try {
			return objectMapper.readValue(cleaned, new TypeReference<List<RawQuestion>>() {
			});
		} catch (JsonProcessingException ex) {
			log.warn("Could not parse AI quiz response as JSON: {}", rawText);
			throw new TeacherAiGenerationException(
					"The AI response could not be parsed as quiz questions. Please try again.", ex);
		}
	}

	private String stripCodeFences(String text) {
		String trimmed = text.trim();
		if (trimmed.startsWith("```")) {
			int firstNewline = trimmed.indexOf('\n');
			int lastFence = trimmed.lastIndexOf("```");
			if (firstNewline != -1 && lastFence > firstNewline) {
				trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
			}
		}
		return trimmed;
	}

	private List<QuestionType> normalizeQuestionTypes(List<QuestionType> questionTypes) {
		if (questionTypes == null || questionTypes.isEmpty()) {
			return DEFAULT_TYPES;
		}
		return questionTypes;
	}

	private record RawQuestion(String question, List<String> options, String answer, String explanation) {
	}

}
