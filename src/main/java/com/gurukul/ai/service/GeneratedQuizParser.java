package com.gurukul.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurukul.ai.dto.QuizGeneratorDtos.GeneratedQuestionType;
import com.gurukul.ai.dto.QuizGeneratorDtos.GeneratedQuizQuestion;
import com.gurukul.gamification.service.QuizAnswerChecker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the model's raw reply into validated questions, or explains exactly what was wrong so the
 * generator can ask once more. Pure (no Spring, no I/O) so every rule is unit-testable.
 *
 * <p>The model is told to return JSON only, but in practice it sometimes wraps it in a ```json
 * fence or adds a sentence first; both are tolerated. What is not tolerated is anything the app or
 * the question bank would trip over later: a wrong question count, a type the teacher didn't ask
 * for, marks that don't add up, an MCQ without exactly four distinct options or whose answer isn't
 * one of them, a NUMERIC answer that isn't a number, a SHORT_WORD answer longer than two words.
 */
public final class GeneratedQuizParser {

	/** Why the model's output was rejected. The message is sent back to the model on retry. */
	public static class InvalidQuizOutputException extends RuntimeException {
		public InvalidQuizOutputException(String message) {
			super(message);
		}
	}

	/** What the output must satisfy. */
	public record Expectations(int questionCount, int maxMarks, Set<GeneratedQuestionType> allowedTypes) {
	}

	static final int MAX_QUESTION_CHARS = 1000;
	static final int MAX_OPTION_CHARS = 255;
	static final int MAX_ANSWER_CHARS = 2000;
	static final int MAX_EXPLANATION_CHARS = 1000;

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final Pattern FENCE = Pattern.compile("```(?:json)?\\s*(.*?)```", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
	/** "A) text", "a. text", "(B) text", "C: text" */
	private static final Pattern OPTION_PREFIX = Pattern.compile("^\\(?([A-Da-d])[).:]\\s*(.*)$", Pattern.DOTALL);
	/** "B", "(B)", "B)", "b.", "Option B" - a bare letter reference to an option. */
	private static final Pattern LETTER_ANSWER = Pattern.compile("^(?:option\\s+)?\\(?([A-Da-d])\\)?[.)]?$", Pattern.CASE_INSENSITIVE);

	private GeneratedQuizParser() {
	}

	public static List<GeneratedQuizQuestion> parse(String raw, Expectations expected) {
		JsonNode root = readJson(raw);
		JsonNode questions = root.isArray() ? root : root.get("questions");
		if (questions == null || !questions.isArray()) {
			throw new InvalidQuizOutputException("The JSON must be an object with a \"questions\" array.");
		}
		if (questions.size() != expected.questionCount()) {
			throw new InvalidQuizOutputException("Expected exactly " + expected.questionCount()
					+ " questions but got " + questions.size() + ".");
		}

		List<GeneratedQuizQuestion> result = new ArrayList<>(questions.size());
		int marksTotal = 0;
		for (int i = 0; i < questions.size(); i++) {
			GeneratedQuizQuestion question = parseQuestion(questions.get(i), i + 1, expected.allowedTypes());
			marksTotal += question.getMarks();
			result.add(question);
		}
		if (marksTotal != expected.maxMarks()) {
			throw new InvalidQuizOutputException("The marks add up to " + marksTotal + " but must add up to exactly "
					+ expected.maxMarks() + ".");
		}
		return result;
	}

	private static JsonNode readJson(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new InvalidQuizOutputException("The reply was empty; return the JSON object.");
		}
		String text = raw.trim();
		Matcher fence = FENCE.matcher(text);
		if (fence.find()) {
			text = fence.group(1).trim();
		}
		int objectStart = text.indexOf('{');
		int arrayStart = text.indexOf('[');
		int start = objectStart < 0 ? arrayStart : (arrayStart < 0 ? objectStart : Math.min(objectStart, arrayStart));
		int end = Math.max(text.lastIndexOf('}'), text.lastIndexOf(']'));
		if (start < 0 || end <= start) {
			throw new InvalidQuizOutputException("The reply was not JSON. Return only the JSON object.");
		}
		try {
			return MAPPER.readTree(text.substring(start, end + 1));
		} catch (Exception ex) {
			throw new InvalidQuizOutputException("The reply was not valid JSON (" + firstLine(ex.getMessage())
					+ "). Return only the JSON object.");
		}
	}

	private static GeneratedQuizQuestion parseQuestion(JsonNode node, int number, Set<GeneratedQuestionType> allowed) {
		String where = "Question " + number + ": ";
		if (node == null || !node.isObject()) {
			throw new InvalidQuizOutputException(where + "must be a JSON object.");
		}

		GeneratedQuestionType type = parseType(text(node, "questionType"), where);
		if (!allowed.contains(type)) {
			throw new InvalidQuizOutputException(where + "type " + type + " was not requested; use only " + allowed + ".");
		}

		String question = text(node, "question");
		if (question.isEmpty()) {
			throw new InvalidQuizOutputException(where + "the question text is missing.");
		}
		if (question.length() > MAX_QUESTION_CHARS) {
			throw new InvalidQuizOutputException(where + "the question is too long (max " + MAX_QUESTION_CHARS + " characters).");
		}

		List<String> options = new ArrayList<>();
		JsonNode optionsNode = node.get("options");
		if (optionsNode != null && optionsNode.isArray()) {
			optionsNode.forEach(o -> options.add(o.isNull() ? "" : o.asText().trim()));
		}

		String answer = text(node, "answer");
		if (answer.isEmpty()) {
			throw new InvalidQuizOutputException(where + "the answer is missing.");
		}

		List<String> finalOptions;
		String finalAnswer;
		switch (type) {
			case MCQ -> {
				finalOptions = stripLetterPrefixes(options);
				if (finalOptions.size() != 4 || finalOptions.stream().anyMatch(String::isEmpty)) {
					throw new InvalidQuizOutputException(where + "a multiple-choice question needs exactly 4 non-empty options.");
				}
				if (finalOptions.stream().map(QuizAnswerChecker::normalizeWords).distinct().count() != 4) {
					throw new InvalidQuizOutputException(where + "the 4 options must all be different.");
				}
				if (finalOptions.stream().anyMatch(o -> o.length() > MAX_OPTION_CHARS)) {
					throw new InvalidQuizOutputException(where + "an option is too long (max " + MAX_OPTION_CHARS + " characters).");
				}
				finalAnswer = matchOption(answer, finalOptions, where);
			}
			case TRUE_FALSE -> {
				finalOptions = options.isEmpty() ? List.of("True", "False") : stripLetterPrefixes(options);
				if (finalOptions.size() != 2 || finalOptions.stream().anyMatch(String::isEmpty)) {
					throw new InvalidQuizOutputException(where + "a true/false question needs exactly 2 options.");
				}
				finalAnswer = matchOption(answer, finalOptions, where);
			}
			case NUMERIC -> {
				if (!QuizAnswerChecker.isValidNumericAnswer(answer)) {
					throw new InvalidQuizOutputException(where + "a NUMERIC answer must be a plain number with no units (got \""
							+ abbreviate(answer) + "\").");
				}
				finalOptions = List.of();
				finalAnswer = answer;
			}
			case SHORT_WORD -> {
				if (!QuizAnswerChecker.isValidShortWordAnswer(answer)) {
					throw new InvalidQuizOutputException(where + "a SHORT_WORD answer must be one or two words (got \""
							+ abbreviate(answer) + "\").");
				}
				finalOptions = List.of();
				finalAnswer = answer.replaceAll("\\s+", " ");
			}
			default -> {
				finalOptions = List.of();
				finalAnswer = answer;
			}
		}
		if (finalAnswer.length() > MAX_ANSWER_CHARS) {
			throw new InvalidQuizOutputException(where + "the answer is too long (max " + MAX_ANSWER_CHARS + " characters).");
		}

		int marks = parseMarks(node.get("marks"), where);
		String explanation = text(node, "explanation");
		if (explanation.length() > MAX_EXPLANATION_CHARS) {
			explanation = explanation.substring(0, MAX_EXPLANATION_CHARS);
		}
		return new GeneratedQuizQuestion(number, type, question, finalOptions, finalAnswer, explanation, marks);
	}

	private static GeneratedQuestionType parseType(String value, String where) {
		String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
		try {
			return GeneratedQuestionType.valueOf(normalized);
		} catch (IllegalArgumentException ex) {
			throw new InvalidQuizOutputException(where + "unknown questionType \"" + abbreviate(value) + "\".");
		}
	}

	private static int parseMarks(JsonNode node, String where) {
		if (node == null || node.isNull()) {
			throw new InvalidQuizOutputException(where + "marks is missing.");
		}
		double value;
		if (node.isNumber()) {
			value = node.asDouble();
		} else {
			try {
				value = Double.parseDouble(node.asText().trim());
			} catch (NumberFormatException ex) {
				throw new InvalidQuizOutputException(where + "marks must be a whole number.");
			}
		}
		if (value != Math.rint(value) || value < 1 || value > 100) {
			throw new InvalidQuizOutputException(where + "marks must be a whole number of at least 1.");
		}
		return (int) value;
	}

	/** Removes "A) " style prefixes, but only when every option carries the matching letter in order. */
	static List<String> stripLetterPrefixes(List<String> options) {
		List<String> stripped = new ArrayList<>(options.size());
		for (int i = 0; i < options.size(); i++) {
			Matcher m = OPTION_PREFIX.matcher(options.get(i));
			if (!m.matches() || Character.toUpperCase(m.group(1).charAt(0)) != (char) ('A' + i)) {
				return options;
			}
			stripped.add(m.group(2).trim());
		}
		return stripped;
	}

	/** The answer as the exact text of one of the options, accepting a letter reference. */
	private static String matchOption(String answer, List<String> options, String where) {
		String normalized = QuizAnswerChecker.normalizeWords(answer);
		for (String option : options) {
			if (QuizAnswerChecker.normalizeWords(option).equals(normalized)) {
				return option;
			}
		}
		Matcher letter = LETTER_ANSWER.matcher(answer.trim());
		if (letter.matches()) {
			int index = Character.toUpperCase(letter.group(1).charAt(0)) - 'A';
			if (index < options.size()) {
				return options.get(index);
			}
		}
		Matcher prefixed = OPTION_PREFIX.matcher(answer.trim());
		if (prefixed.matches()) {
			int index = Character.toUpperCase(prefixed.group(1).charAt(0)) - 'A';
			if (index < options.size()
					&& QuizAnswerChecker.normalizeWords(options.get(index)).equals(QuizAnswerChecker.normalizeWords(prefixed.group(2)))) {
				return options.get(index);
			}
		}
		throw new InvalidQuizOutputException(where + "the answer must be exactly the text of one of the options.");
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? "" : value.asText().trim();
	}

	private static String abbreviate(String value) {
		return value.length() <= 40 ? value : value.substring(0, 40) + "...";
	}

	private static String firstLine(String message) {
		if (message == null) {
			return "parse error";
		}
		int newline = message.indexOf('\n');
		String line = newline < 0 ? message : message.substring(0, newline);
		return line.length() <= 120 ? line : line.substring(0, 120);
	}

}
