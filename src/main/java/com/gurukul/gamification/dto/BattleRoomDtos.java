package com.gurukul.gamification.dto;

import com.gurukul.gamification.entity.BattleRoomStatus;
import com.gurukul.gamification.entity.QuizOption;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class BattleRoomDtos {

	@Getter @Setter
	@Schema(name = "CreateBattleRoomRequest")
	public static class CreateBattleRoomRequest {
		@NotNull private UUID subjectId;
	}

	@Getter @Setter
	@Schema(name = "MatchBattleRoomRequest")
	public static class MatchBattleRoomRequest {
		@NotNull private UUID subjectId;
	}

	@Getter @Setter
	@Schema(name = "JoinByCodeRequest")
	public static class JoinByCodeRequest {
		@NotBlank private String code;
	}

	@Getter @Setter
	@Schema(name = "SubmitBattleAnswerRequest")
	public static class SubmitBattleAnswerRequest {
		@NotNull private QuizOption selectedOption;
	}

	@Getter @AllArgsConstructor
	@Schema(name = "BattleParticipantResponse")
	public static class BattleParticipantResponse {
		private UUID studentId;
		private String name;
		@Schema(description = "Total score: each correct answer earns 1-10 by speed, wrong answers 0")
		private int points;
		private int correctCount;
		@Schema(description = "Whether they've locked in an answer to currentQuestion - never whether it's "
				+ "right, that's only revealed in lastResult once the question closes")
		private boolean answeredCurrentQuestion;
	}

	@Getter @AllArgsConstructor
	@Schema(name = "BattleRoomResponse", description = "Full room snapshot - also the shape broadcast over "
			+ "/topic/battle-rooms/{roomId} on every state change")
	public static class BattleRoomResponse {
		private UUID id;
		private String roomCode;
		private String className;
		private String subjectName;
		private BattleRoomStatus status;
		private int minPlayers;
		private int maxPlayers;
		private int joinWindowSeconds;
		@Schema(description = "Absolute deadline for the join window (only meaningful while WAITING) - "
				+ "compute remaining time client-side as joinWindowEndsAt - now(), don't rely on push cadence")
		private Instant joinWindowEndsAt;
		private int questionCount;
		private int currentQuestionIndex;
		@Schema(description = "Ordered by points, highest first")
		private List<BattleParticipantResponse> participants;
		private ArenaDtos.PublicQuizQuestionResponse currentQuestion;
		@Schema(description = "When answering opens for currentQuestion. In the future during the reveal pause "
				+ "after the previous question - hide the question and count down to this instead; answers are "
				+ "rejected until then")
		private Instant currentQuestionStartsAt;
		@Schema(description = "When answering closes for currentQuestion. The question closes earlier if every "
				+ "participant answers")
		private Instant currentQuestionEndsAt;
		@Schema(description = "Every participant's result for the most recently closed question, plus its correct "
				+ "option - null before the first question closes")
		private BattleQuestionResultResponse lastResult;
		private UUID winnerStudentId;
		private String winnerName;
	}

	@Getter @AllArgsConstructor
	@Schema(name = "BattleQuestionResultResponse", description = "Revealed once a question closes - never sent "
			+ "for the question still in play")
	public static class BattleQuestionResultResponse {
		private int questionIndex;
		private UUID questionId;
		private QuizOption correctOption;
		@Schema(description = "One row per participant, fastest correct answer first")
		private List<BattlePlayerResultResponse> results;
	}

	@Getter @AllArgsConstructor
	@Schema(name = "BattlePlayerResultResponse")
	public static class BattlePlayerResultResponse {
		private UUID studentId;
		private String name;
		private boolean answered;
		@Schema(description = "Null when they didn't answer in time")
		private QuizOption selectedOption;
		private boolean correct;
		@Schema(description = "Points earned on this question: 1-10 by speed if correct, else 0")
		private int points;
		@Schema(description = "Null when they didn't answer in time")
		private Integer responseMs;
	}

	@Getter @AllArgsConstructor
	@Schema(name = "BattleRoomSummaryResponse", description = "Lightweight row for browsing open rooms - "
			+ "use GET /battle-rooms/{id} for the full state once a student taps in")
	public static class BattleRoomSummaryResponse {
		private UUID id;
		private String roomCode;
		private String subjectName;
		private String className;
		private BattleRoomStatus status;
		private int participantCount;
		private int maxPlayers;
	}

	@Getter @AllArgsConstructor
	@Schema(name = "SubmitBattleAnswerResponse", description = "Only confirms the answer was locked in - whether "
			+ "it's right is revealed to everyone in lastResult once the question closes")
	public static class SubmitBattleAnswerResponse {
		private int questionIndex;
		private boolean roomCompleted;
	}

}
