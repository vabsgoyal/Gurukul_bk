package com.gurukul.chat.dto;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.chat.entity.Conversation;
import com.gurukul.chat.entity.ConversationParticipant;
import com.gurukul.chat.entity.ConversationType;
import com.gurukul.chat.entity.Message;
import com.gurukul.chat.entity.SenderKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ChatDtos {

	@Getter @Setter
	@Schema(description = "Create (or fetch, if one already exists) a 1:1 conversation with another party")
	public static class CreateConversationRequest {
		@NotNull private OwnerType otherPartyOwnerType;
		@NotNull private UUID otherPartyOwnerId;
	}

	@Getter @AllArgsConstructor
	public static class ParticipantResponse {
		private OwnerType ownerType;
		private UUID ownerId;

		public static ParticipantResponse from(ConversationParticipant participant) {
			return new ParticipantResponse(participant.getOwnerType(), participant.getOwnerId());
		}
	}

	@Getter @AllArgsConstructor
	public static class ConversationResponse {
		private UUID id;
		private ConversationType type;
		private List<ParticipantResponse> participants;

		public static ConversationResponse from(Conversation conversation, List<ConversationParticipant> participants) {
			return new ConversationResponse(
					conversation.getId(),
					conversation.getType(),
					participants.stream().map(ParticipantResponse::from).toList());
		}
	}

	@Getter @Setter
	public static class SendMessageRequest {
		@NotBlank private String content;
	}

	@Getter @AllArgsConstructor
	public static class MessageResponse {
		private UUID id;
		private SenderKind senderKind;
		private OwnerType senderOwnerType;
		private UUID senderOwnerId;
		private String content;
		private Instant sentAt;

		public static MessageResponse from(Message message) {
			return new MessageResponse(
					message.getId(),
					message.getSenderKind(),
					message.getSenderOwnerType(),
					message.getSenderOwnerId(),
					message.getContent(),
					message.getSentAt());
		}
	}

	@Getter @AllArgsConstructor
	public static class MessagePageResponse {
		private List<MessageResponse> messages;
		private boolean hasMore;
	}

}
