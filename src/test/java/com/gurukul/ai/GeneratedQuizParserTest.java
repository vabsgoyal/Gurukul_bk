package com.gurukul.ai;

import com.gurukul.ai.dto.QuizGeneratorDtos.GeneratedQuestionType;
import com.gurukul.ai.dto.QuizGeneratorDtos.GeneratedQuizQuestion;
import com.gurukul.ai.service.GeneratedQuizParser;
import com.gurukul.ai.service.GeneratedQuizParser.Expectations;
import com.gurukul.ai.service.GeneratedQuizParser.InvalidQuizOutputException;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneratedQuizParserTest {

	private static final Expectations ANY_THREE_FOR_FIVE =
			new Expectations(3, 5, EnumSet.allOf(GeneratedQuestionType.class));

	static final String VALID = """
			{"questions": [
			  {"questionType": "MCQ", "question": "2 + 2 = ?", "options": ["3", "4", "5", "6"], "answer": "4", "explanation": "Basic addition.", "marks": 1},
			  {"questionType": "NUMERIC", "question": "Half of 5?", "options": [], "answer": "2.5", "explanation": "5 / 2.", "marks": 2},
			  {"questionType": "SHORT_WORD", "question": "Capital of India?", "options": [], "answer": "New  Delhi", "explanation": "", "marks": 2}
			]}
			""";

	@Test
	void parsesAValidQuizAndNumbersIt() {
		List<GeneratedQuizQuestion> questions = GeneratedQuizParser.parse(VALID, ANY_THREE_FOR_FIVE);
		assertThat(questions).extracting(GeneratedQuizQuestion::getNumber).containsExactly(1, 2, 3);
		assertThat(questions.get(0).getAnswer()).isEqualTo("4");
		assertThat(questions.get(1).getQuestionType()).isEqualTo(GeneratedQuestionType.NUMERIC);
		assertThat(questions.get(2).getAnswer()).isEqualTo("New Delhi");
	}

	@Test
	void toleratesCodeFencesAndLeadingProse() {
		String wrapped = "Here is your quiz:\n```json\n" + VALID + "\n```\nGood luck!";
		assertThat(GeneratedQuizParser.parse(wrapped, ANY_THREE_FOR_FIVE)).hasSize(3);
	}

	@Test
	void normalisesLetterAnswersAndOptionPrefixes() {
		String json = """
				{"questions": [{"questionType": "mcq", "question": "Largest planet?",
				  "options": ["A) Mars", "B) Jupiter", "C) Venus", "D) Earth"], "answer": "B", "marks": 3}]}
				""";
		GeneratedQuizQuestion q = GeneratedQuizParser.parse(json,
				new Expectations(1, 3, EnumSet.of(GeneratedQuestionType.MCQ))).get(0);
		assertThat(q.getOptions()).containsExactly("Mars", "Jupiter", "Venus", "Earth");
		assertThat(q.getAnswer()).isEqualTo("Jupiter");
	}

	@Test
	void trueFalseDefaultsItsOptions() {
		String json = """
				{"questions": [{"questionType": "TRUE_FALSE", "question": "The sun is a star.", "answer": "true", "marks": 1}]}
				""";
		GeneratedQuizQuestion q = GeneratedQuizParser.parse(json,
				new Expectations(1, 1, EnumSet.of(GeneratedQuestionType.TRUE_FALSE))).get(0);
		assertThat(q.getOptions()).containsExactly("True", "False");
		assertThat(q.getAnswer()).isEqualTo("True");
	}

	@Test
	void rejectsNonJson() {
		assertInvalid("Sorry, I cannot help with that.", ANY_THREE_FOR_FIVE, "not JSON");
		assertInvalid("{\"questions\": [", ANY_THREE_FOR_FIVE, "not");
		assertInvalid("", ANY_THREE_FOR_FIVE, "empty");
		assertInvalid("{\"items\": []}", ANY_THREE_FOR_FIVE, "\"questions\" array");
	}

	@Test
	void rejectsTheWrongQuestionCount() {
		assertInvalid(VALID, new Expectations(4, 5, EnumSet.allOf(GeneratedQuestionType.class)), "exactly 4 questions but got 3");
	}

	@Test
	void rejectsMarksThatDoNotAddUp() {
		assertInvalid(VALID, new Expectations(3, 10, EnumSet.allOf(GeneratedQuestionType.class)),
				"add up to 5 but must add up to exactly 10");
	}

	@Test
	void rejectsTypesTheTeacherDidNotAskFor() {
		assertInvalid(VALID, new Expectations(3, 5, EnumSet.of(GeneratedQuestionType.MCQ)), "Question 2: type NUMERIC was not requested");
	}

	@Test
	void mcqNeedsFourDistinctOptionsAndAnAnswerAmongThem() {
		Expectations one = new Expectations(1, 1, EnumSet.of(GeneratedQuestionType.MCQ));
		assertInvalid(mcq("[\"1\", \"2\", \"3\"]", "1"), one, "exactly 4 non-empty options");
		assertInvalid(mcq("[\"1\", \"2\", \"2\", \"3\"]", "1"), one, "must all be different");
		assertInvalid(mcq("[\"1\", \"2\", \"3\", \"4\"]", "7"), one, "one of the options");
		assertInvalid(mcq("[\"1\", \"2\", \"3\", \"4\"]", ""), one, "answer is missing");
	}

	@Test
	void numericAndShortWordAnswersAreChecked() {
		Expectations numeric = new Expectations(1, 1, EnumSet.of(GeneratedQuestionType.NUMERIC));
		assertInvalid(single("NUMERIC", "5 kg", 1), numeric, "plain number");
		Expectations word = new Expectations(1, 1, EnumSet.of(GeneratedQuestionType.SHORT_WORD));
		assertInvalid(single("SHORT_WORD", "the water cycle", 1), word, "one or two words");
	}

	@Test
	void marksMustBeWholeAndPositive() {
		assertInvalid(single("LONG_ANSWER", "Because...", 0),
				new Expectations(1, 1, EnumSet.of(GeneratedQuestionType.LONG_ANSWER)), "at least 1");
		assertInvalid(single("LONG_ANSWER", "Because...", 1.5),
				new Expectations(1, 2, EnumSet.of(GeneratedQuestionType.LONG_ANSWER)), "whole number");
	}

	private static String mcq(String options, String answer) {
		return "{\"questions\": [{\"questionType\": \"MCQ\", \"question\": \"Q?\", \"options\": " + options
				+ ", \"answer\": \"" + answer + "\", \"marks\": 1}]}";
	}

	private static String single(String type, String answer, Number marks) {
		return "{\"questions\": [{\"questionType\": \"" + type + "\", \"question\": \"Q?\", \"options\": [], \"answer\": \""
				+ answer + "\", \"marks\": " + marks + "}]}";
	}

	private static void assertInvalid(String raw, Expectations expected, String messagePart) {
		assertThatThrownBy(() -> GeneratedQuizParser.parse(raw, expected))
				.isInstanceOf(InvalidQuizOutputException.class)
				.hasMessageContaining(messagePart);
	}

}
