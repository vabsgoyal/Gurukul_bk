package com.gurukul.gamification.dto;

import com.gurukul.gamification.entity.ChallengeStatus;
import com.gurukul.gamification.entity.QuizOption;
import com.gurukul.gamification.entity.QuizQuestion;
import com.gurukul.gamification.entity.QuizQuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

public class ArenaDtos {

	@Getter @Setter
	@Schema(name = "CreateQuizQuestionRequest")
	public static class CreateQuizQuestionRequest {
		@NotNull private UUID subjectId;
		@NotBlank
		@Schema(description = "Grade this question is scoped to, e.g. \"Grade 8\" - shared across all "
				+ "that grade's sections", example = "Grade 8")
		private String className;
		@NotBlank private String questionText;
		@NotBlank private String optionA;
		@NotBlank private String optionB;
		@NotBlank private String optionC;
		@NotBlank private String optionD;
		@NotNull private QuizOption correctOption;
	}

	/**
	 * Saves several reviewed questions (typically from the AI quiz generator) for one subject + grade
	 * in a single all-or-nothing transaction. Per-type field rules are checked in ArenaService, not
	 * here, because which fields are required depends on questionType.
	 */
	@Getter @Setter
	@Schema(name = "BulkCreateQuizQuestionsRequest")
	public static class BulkCreateQuizQuestionsRequest {
		@NotNull private UUID subjectId;
		@NotBlank
		@Size(max = 255)
		@Schema(description = "Grade the questions are scoped to, e.g. \"Grade 8\"", example = "Grade 8")
		private String className;
		@NotEmpty
		@Size(max = 50, message = "must contain at most 50 questions")
		@Valid
		private List<BankQuestionInput> questions;
	}

	@Getter @Setter
	@Schema(name = "BankQuestionInput", description = "MCQ needs optionA-D + correctOption; NUMERIC needs a "
			+ "number in answerText; SHORT_WORD needs a one- or two-word answerText")
	public static class BankQuestionInput {
		@NotNull private QuizQuestionType questionType;
		@NotBlank @Size(max = 500) private String questionText;
		@Size(max = 255) private String optionA;
		@Size(max = 255) private String optionB;
		@Size(max = 255) private String optionC;
		@Size(max = 255) private String optionD;
		private QuizOption correctOption;
		@Size(max = 255) private String answerText;
	}

	@Getter @AllArgsConstructor
	@Schema(name = "QuizQuestionResponse", description = "Teacher/admin view - includes the correct answer")
	public static class QuizQuestionResponse {
		private UUID id;
		private String className;
		private String questionText;
		private String optionA;
		private String optionB;
		private String optionC;
		private String optionD;
		private QuizOption correctOption;
		private UUID createdByEmployeeId;
		private String createdByEmployeeName;
		@Schema(description = "MCQ (options A-D + correctOption), NUMERIC or SHORT_WORD (answerText)")
		private QuizQuestionType questionType;
		@Schema(description = "Expected answer for NUMERIC / SHORT_WORD questions; null for MCQ")
		private String answerText;

		public static QuizQuestionResponse from(QuizQuestion q) {
			return new QuizQuestionResponse(
					q.getId(), q.getClassName(), q.getQuestionText(), q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD(), q.getCorrectOption(),
					q.getCreatedByTeacher().getId(), q.getCreatedByTeacher().getName(), q.getQuestionType(), q.getAnswerText());
		}
	}

	@Getter @AllArgsConstructor
	@Schema(name = "PublicQuizQuestionResponse", description = "Student-facing view - never includes the correct answer")
	public static class PublicQuizQuestionResponse {
		private UUID id;
		private String questionText;
		private String optionA;
		private String optionB;
		private String optionC;
		private String optionD;

		public static PublicQuizQuestionResponse from(QuizQuestion q) {
			return new PublicQuizQuestionResponse(
					q.getId(), q.getQuestionText(), q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD());
		}
	}

	@Getter @Setter
	@Schema(name = "CreateChallengeRequest")
	public static class CreateChallengeRequest {
		@NotNull private UUID opponentStudentId;
		@NotNull private UUID subjectId;
	}

	@Getter @Setter
	@Schema(name = "SubmitAnswerRequest")
	public static class SubmitAnswerRequest {
		@NotNull private UUID questionId;
		@NotNull private QuizOption selectedOption;
	}

	@Getter @AllArgsConstructor
	@Schema(name = "SubmitAnswerResponse")
	public static class SubmitAnswerResponse {
		private boolean correct;
		private boolean challengeCompleted;
		private QuizOption correctOption;
	}

	@Getter @AllArgsConstructor
	@Schema(name = "ChallengeSummaryResponse")
	public static class ChallengeSummaryResponse {
		private UUID id;
		private String subjectName;
		private String opponentName;
		private ChallengeStatus status;
		private int totalQuestions;
		private int myAnsweredCount;
		private int opponentAnsweredCount;
		private Boolean youWon;
		private boolean draw;
	}

	@Getter @AllArgsConstructor
	@Schema(name = "ChallengeDetailResponse")
	public static class ChallengeDetailResponse {
		private ChallengeSummaryResponse summary;
		private List<PublicQuizQuestionResponse> questions;
		private List<UUID> myAnsweredQuestionIds;
	}

}
