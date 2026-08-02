package com.gurukul.gamification.dto;

import com.gurukul.gamification.entity.PracticeSessionStatus;
import com.gurukul.gamification.entity.QuizOption;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

public class PracticeDtos {

	@Getter @Setter
	@Schema(name = "CreatePracticeSessionRequest")
	public static class CreatePracticeSessionRequest {
		@NotNull private UUID subjectId;
	}

	@Getter @Setter
	@Schema(name = "SubmitPracticeAnswerRequest")
	public static class SubmitPracticeAnswerRequest {
		@NotNull private UUID questionId;
		@NotNull private QuizOption selectedOption;
	}

	@Getter @AllArgsConstructor
	@Schema(name = "SubmitPracticeAnswerResponse")
	public static class SubmitPracticeAnswerResponse {
		private boolean correct;
		private boolean sessionCompleted;
	}

	@Getter @AllArgsConstructor
	@Schema(name = "PracticeSessionResponse", description = "No XP is awarded for practice - this is prep, not competition")
	public static class PracticeSessionResponse {
		private UUID id;
		private String subjectName;
		private PracticeSessionStatus status;
		private int totalQuestions;
		private int answeredCount;
		private int correctCount;
		private List<ArenaDtos.PublicQuizQuestionResponse> questions;
		private List<UUID> myAnsweredQuestionIds;
	}

}
