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
		private int correctCount;
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
		private List<BattleParticipantResponse> participants;
		private ArenaDtos.PublicQuizQuestionResponse currentQuestion;
		private UUID currentBuzzWinnerStudentId;
		private Boolean lastAnswerCorrect;
		private UUID winnerStudentId;
		private String winnerName;
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
	@Schema(name = "BuzzResponse")
	public static class BuzzResponse {
		private boolean won;
	}

	@Getter @AllArgsConstructor
	@Schema(name = "SubmitBattleAnswerResponse")
	public static class SubmitBattleAnswerResponse {
		private boolean correct;
		private boolean roomCompleted;
	}

}
