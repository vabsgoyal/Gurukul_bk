package com.gurukul.teachers;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurukul.chat.bot.config.AnthropicProperties;
import com.gurukul.common.SchoolContext;
import com.gurukul.students.entity.ClassSection;
import com.gurukul.teachers.dto.AiQuizGenerationRequest;
import com.gurukul.teachers.dto.AiQuizGenerationResponse;
import com.gurukul.teachers.entity.AssessmentType;
import com.gurukul.teachers.entity.QuestionType;
import com.gurukul.teachers.entity.QuizDifficulty;
import com.gurukul.teachers.entity.Teacher;
import com.gurukul.teachers.service.AnthropicTeacherAiQuizGenerator;
import com.gurukul.teachers.service.TeacherAiGenerationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnthropicTeacherAiQuizGeneratorTest {

	private final AnthropicClient anthropicClient = mock(AnthropicClient.class);
	private final SchoolContext schoolContext = mock(SchoolContext.class);
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final AnthropicProperties properties =
			new AnthropicProperties("bedrock", "", "claude-opus-5", "MEDIUM", 4096, 5, 10);

	private final AnthropicTeacherAiQuizGenerator generator =
			new AnthropicTeacherAiQuizGenerator(anthropicClient, properties, schoolContext, objectMapper);

	private Teacher teacher() {
		Teacher teacher = new Teacher();
		teacher.setId(UUID.randomUUID());
		teacher.setName("Asha Rao");
		return teacher;
	}

	private ClassSection classSection() {
		ClassSection classSection = new ClassSection();
		classSection.setId(UUID.randomUUID());
		classSection.setClassName("Grade 6");
		classSection.setSection("A");
		classSection.setAcademicYear("2026-27");
		return classSection;
	}

	private AiQuizGenerationRequest request(int questionCount, int maxMarks) {
		AiQuizGenerationRequest request = new AiQuizGenerationRequest();
		request.setClassSectionId(UUID.randomUUID());
		request.setSubjectName("Science");
		request.setAssessmentType(AssessmentType.QUIZ);
		request.setTitle("Photosynthesis Quick Check");
		request.setSyllabus("Photosynthesis, chlorophyll, stomata");
		request.setDifficulty(QuizDifficulty.MEDIUM);
		request.setQuestionCount(questionCount);
		request.setMaxMarks(maxMarks);
		request.setQuestionTypes(List.of(QuestionType.MCQ, QuestionType.SHORT_ANSWER));
		return request;
	}

	private void mockAnthropicResponse(String text) {
		TextBlock textBlock = mock(TextBlock.class);
		when(textBlock.text()).thenReturn(text);
		ContentBlock contentBlock = mock(ContentBlock.class);
		when(contentBlock.isText()).thenReturn(true);
		when(contentBlock.asText()).thenReturn(textBlock);
		com.anthropic.models.messages.Message anthropicResponse = mock(com.anthropic.models.messages.Message.class);
		when(anthropicResponse.content()).thenReturn(List.of(contentBlock));

		com.anthropic.services.blocking.MessageService messagesApi =
				mock(com.anthropic.services.blocking.MessageService.class);
		when(messagesApi.create(any(MessageCreateParams.class))).thenReturn(anthropicResponse);
		when(anthropicClient.messages()).thenReturn(messagesApi);
	}

	@Test
	void parsesModelJsonIntoQuestionsWithComputedNumberTypeAndMarks() {
		mockAnthropicResponse("""
				[
				  {"question": "What pigment captures light for photosynthesis?", "options": ["Chlorophyll", "Melanin", "Keratin", "Insulin"], "answer": "Chlorophyll", "explanation": "Chlorophyll absorbs light energy."},
				  {"question": "Explain the role of stomata in photosynthesis.", "options": [], "answer": "Stomata allow gas exchange for photosynthesis.", "explanation": "Checks understanding of gas exchange."}
				]
				""");

		AiQuizGenerationResponse response = generator.generate(teacher(), classSection(), request(2, 5));

		assertThat(response.getGeneratorMode()).isEqualTo("ANTHROPIC_BEDROCK");
		assertThat(response.getQuestions()).hasSize(2);
		assertThat(response.getQuestions().get(0).getQuestionType()).isEqualTo(QuestionType.MCQ);
		assertThat(response.getQuestions().get(1).getQuestionType()).isEqualTo(QuestionType.SHORT_ANSWER);
		assertThat(response.getQuestions().stream().mapToInt(q -> q.getMarks()).sum()).isEqualTo(5);
		assertThat(response.getQuestions().get(0).getNumber()).isEqualTo(1);
		assertThat(response.getQuestions().get(0).getOptions()).contains("Chlorophyll");
	}

	@Test
	void stripsMarkdownCodeFencesBeforeParsing() {
		mockAnthropicResponse("""
				```json
				[{"question": "Q1?", "options": [], "answer": "A1", "explanation": "E1"}]
				```
				""");

		AiQuizGenerationResponse response = generator.generate(teacher(), classSection(), request(1, 10));

		assertThat(response.getQuestions()).hasSize(1);
		assertThat(response.getQuestions().get(0).getMarks()).isEqualTo(10);
	}

	@Test
	void wrongQuestionCountThrowsGenerationException() {
		mockAnthropicResponse("""
				[{"question": "Only one question", "options": [], "answer": "A", "explanation": "E"}]
				""");

		assertThatThrownBy(() -> generator.generate(teacher(), classSection(), request(3, 10)))
				.isInstanceOf(TeacherAiGenerationException.class)
				.hasMessageContaining("3 were requested");
	}

	@Test
	void unparseableResponseThrowsGenerationException() {
		mockAnthropicResponse("This is not JSON at all.");

		assertThatThrownBy(() -> generator.generate(teacher(), classSection(), request(1, 10)))
				.isInstanceOf(TeacherAiGenerationException.class);
	}

}
