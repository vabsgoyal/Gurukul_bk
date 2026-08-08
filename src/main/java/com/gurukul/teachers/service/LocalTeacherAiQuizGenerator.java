package com.gurukul.teachers.service;

import com.gurukul.common.SchoolContext;
import com.gurukul.students.entity.ClassSection;
import com.gurukul.teachers.dto.AiQuizGenerationRequest;
import com.gurukul.teachers.dto.AiQuizGenerationResponse;
import com.gurukul.teachers.dto.GeneratedQuizQuestionResponse;
import com.gurukul.teachers.entity.QuestionType;
import com.gurukul.teachers.entity.Teacher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.teacher-ai", name = "quiz-generator", havingValue = "local", matchIfMissing = true)
public class LocalTeacherAiQuizGenerator implements TeacherAiQuizGenerator {

	private static final List<QuestionType> DEFAULT_TYPES = List.of(QuestionType.MCQ, QuestionType.SHORT_ANSWER);

	private final SchoolContext schoolContext;

	@Override
	public AiQuizGenerationResponse generate(
			Teacher teacher,
			ClassSection classSection,
			AiQuizGenerationRequest request) {
		List<String> topics = extractTopics(request.getSyllabus());
		List<QuestionType> questionTypes = normalizeQuestionTypes(request.getQuestionTypes());
		List<Integer> marks = QuizMarksAllocator.distribute(request.getMaxMarks(), request.getQuestionCount());

		List<GeneratedQuizQuestionResponse> questions = new ArrayList<>();
		for (int index = 0; index < request.getQuestionCount(); index++) {
			QuestionType type = questionTypes.get(index % questionTypes.size());
			String topic = topics.get(index % topics.size());
			questions.add(buildQuestion(index + 1, type, topic, request, marks.get(index)));
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
				"LOCAL_DETERMINISTIC_GENERATOR",
				"Review before publishing. Replace LocalTeacherAiQuizGenerator with a model-backed implementation when AI credentials are configured.",
				questions
		);
	}

	private GeneratedQuizQuestionResponse buildQuestion(
			int number,
			QuestionType type,
			String topic,
			AiQuizGenerationRequest request,
			int marks) {
		return switch (type) {
			case MCQ -> new GeneratedQuizQuestionResponse(
					number,
					type,
					"What is the most accurate statement about " + topic + "?",
					List.of(
							"The core idea of " + topic,
							"An unrelated fact about another topic",
							"A partially correct but incomplete idea",
							"None of the above"
					),
					"The core idea of " + topic,
					"This checks conceptual recall for " + topic + " at " + request.getDifficulty().name().toLowerCase(Locale.ROOT) + " level.",
					marks);
			case SHORT_ANSWER -> new GeneratedQuizQuestionResponse(
					number,
					type,
					"Explain " + topic + " in 2-3 sentences.",
					List.of(),
					"A correct answer should define " + topic + " and mention one important classroom example.",
					"This checks whether the student can explain the topic in their own words.",
					marks);
			case LONG_ANSWER -> new GeneratedQuizQuestionResponse(
					number,
					type,
					"Describe " + topic + " in detail and include one example or diagram where relevant.",
					List.of(),
					"A strong answer should include definition, key points, example, and conclusion for " + topic + ".",
					"This checks deeper understanding and organized written expression.",
					marks);
			case TRUE_FALSE -> new GeneratedQuizQuestionResponse(
					number,
					type,
					"True or false: " + topic + " is an important part of " + request.getSubjectName() + ".",
					List.of("True", "False"),
					"True",
					"This checks basic recognition of syllabus relevance.",
					marks);
		};
	}

	private List<String> extractTopics(String syllabus) {
		List<String> topics = new ArrayList<>();
		for (String rawTopic : syllabus.split("[,;\\n]")) {
			String topic = rawTopic.trim();
			if (!topic.isBlank()) {
				topics.add(topic);
			}
		}
		if (topics.isEmpty()) {
			topics.add(syllabus.trim());
		}
		return topics;
	}

	private List<QuestionType> normalizeQuestionTypes(List<QuestionType> questionTypes) {
		if (questionTypes == null || questionTypes.isEmpty()) {
			return DEFAULT_TYPES;
		}
		return questionTypes;
	}

}
