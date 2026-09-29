package com.gurukul.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * Wire contract for the AI quiz generator. Mirrors the app's AiQuizGenerationRequest /
 * AiQuizGenerationResponse (src/api/types.ts) field for field; subjectId, the NUMERIC / SHORT_WORD
 * question types and the response's model field are compatible additions.
 */
public class QuizGeneratorDtos {

	@Schema(name = "TeacherAssessmentType")
	public enum TeacherAssessmentType { QUIZ, TEST, EXAM, ASSIGNMENT_CHECK }

	@Schema(name = "QuizDifficulty")
	public enum QuizDifficulty { EASY, MEDIUM, HARD, MIXED }

	/**
	 * Question formats the generator can write. MCQ, NUMERIC and SHORT_WORD can later be saved to
	 * the Arena question bank; SHORT_ANSWER and LONG_ANSWER are for printed/shared papers only, and
	 * TRUE_FALSE has two options so it doesn't fit the four-option bank either.
	 */
	@Schema(name = "GeneratedQuestionType")
	public enum GeneratedQuestionType { MCQ, SHORT_ANSWER, LONG_ANSWER, TRUE_FALSE, NUMERIC, SHORT_WORD }

	@Getter
	@Setter
	@Schema(name = "AiQuizGenerationRequest")
	public static class AiQuizGenerationRequest {

		@NotNull private UUID classSectionId;

		@Schema(description = "The subject, from the section's subject assignments. Required for a teacher "
				+ "caller (it is what their assignment is checked against); optional for an admin, in which case "
				+ "subjectName is used as-is")
		private UUID subjectId;

		@NotBlank @Size(max = 100) private String subjectName;
		@NotNull private TeacherAssessmentType assessmentType;
		@NotBlank @Size(max = 200) private String title;

		@NotBlank
		@Size(max = 4000, message = "must be at most 4000 characters")
		@Schema(description = "Chapters/topics to cover, in the teacher's own words")
		private String syllabus;

		@NotNull private QuizDifficulty difficulty;

		@NotNull @Min(1) @Max(50)
		@Schema(description = "Capped server-side by app.quizgen.max-questions (default 30)")
		private Integer questionCount;

		@NotNull @Min(1) @Max(500)
		@Schema(description = "Total marks; the per-question marks must add up to this")
		private Integer maxMarks;

		@Size(max = 6)
		@Schema(description = "Formats to use; omitted or empty means the model picks a sensible mix")
		private List<GeneratedQuestionType> questionTypes;

		@Size(max = 1000) private String additionalInstructions;
	}

	@Getter
	@AllArgsConstructor
	@Schema(name = "GeneratedQuizQuestion")
	public static class GeneratedQuizQuestion {
		private int number;
		private GeneratedQuestionType questionType;
		private String question;
		@Schema(description = "Four options for MCQ, [\"True\", \"False\"] for TRUE_FALSE, empty otherwise")
		private List<String> options;
		@Schema(description = "For MCQ the exact text of the correct option")
		private String answer;
		private String explanation;
		private int marks;
	}

	@Getter
	@AllArgsConstructor
	@Schema(name = "AiQuizGenerationResponse", description = "A draft only - nothing is saved. The teacher "
			+ "reviews it and may save the auto-markable questions via POST /api/v1/gamification/arena/questions/bulk")
	public static class AiQuizGenerationResponse {
		private UUID schoolId;
		private UUID teacherId;
		private String teacherName;
		private UUID classSectionId;
		private String classSectionLabel;
		@Schema(description = "Grade of the section, e.g. \"Grade 8\" - what the question bank is scoped to")
		private String className;
		private UUID subjectId;
		private String subjectName;
		private TeacherAssessmentType assessmentType;
		private String title;
		private String syllabus;
		private QuizDifficulty difficulty;
		private int maxMarks;
		private int questionCount;
		@Schema(description = "\"AI\" for a model-generated draft")
		private String generatorMode;
		private String reviewNote;
		private String model;
		private List<GeneratedQuizQuestion> questions;
	}

}
