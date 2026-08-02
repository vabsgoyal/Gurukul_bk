package com.gurukul.gamification.dto;

import com.gurukul.gamification.entity.ChallengeStatus;
import com.gurukul.gamification.entity.QuizOption;
import com.gurukul.gamification.entity.QuizQuestion;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
		@NotBlank private String questionText;
		@NotBlank private String optionA;
		@NotBlank private String optionB;
		@NotBlank private String optionC;
		@NotBlank private String optionD;
		@NotNull private QuizOption correctOption;
	}

	@Getter @AllArgsConstructor
	@Schema(name = "QuizQuestionResponse", description = "Teacher/admin view - includes the correct answer")
	public static class QuizQuestionResponse {
		private UUID id;
		private String questionText;
		private String optionA;
		private String optionB;
		private String optionC;
		private String optionD;
		private QuizOption correctOption;

		public static QuizQuestionResponse from(QuizQuestion q) {
			return new QuizQuestionResponse(
					q.getId(), q.getQuestionText(), q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD(), q.getCorrectOption());
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
